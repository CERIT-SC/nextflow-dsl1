/*
 * Copyright 2013-2023, Seqera Labs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package nextflow.trace

import java.nio.file.Path
import java.util.concurrent.locks.Lock
import java.util.concurrent.locks.ReentrantLock

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import nextflow.Session
import nextflow.cache.CacheDB
import nextflow.dag.DAG
import nextflow.file.FileHelper
import nextflow.processor.PublishDir.Mode
import nextflow.processor.TaskHandler
import nextflow.processor.TaskProcessor
import nextflow.processor.TaskRun
import nextflow.script.params.FileOutParam
/**
 * Delete task directories once they are no longer needed.
 *
 * @author Ben Sherman <bentshermann@gmail.com>
 */
@Slf4j
@CompileStatic
class TaskCleanupObserver implements TraceObserver {

    private DAG dag

    private CacheDB cache

    private Map<String,ProcessState> processes = [:]

    private Map<TaskRun,TaskState> tasks = [:]

    private Map<Path,PathState> paths = [:]

    private Set<Path> publishedOutputs = []

    private Lock sync = new ReentrantLock()

    @Override
    void onFlowCreate(Session session) {
        this.dag = session.dag
        this.cache = session.cache
    }

    /**
     * When the workflow begins, determine the consumers of each process
     * in the DAG.
     */
    @Override
    void onFlowBegin() {

        for( def processNode : dag.vertices ) {
            // skip nodes that are not processes
            if( !processNode.process )
                continue

            // find all downstream processes in the abstract dag
            def processName = processNode.process.name
            def consumers = [] as Set
            def queue = [ processNode ]

            while( !queue.isEmpty() ) {
                // remove a node from the search queue
                final sourceNode = queue.remove(0)

                // search each outgoing edge from the source node
                for( def edge : dag.edges ) {
                    if( edge.from != sourceNode )
                        continue

                    def node = edge.to

                    // skip if process is terminal
                    if( !node )
                        continue

                    // add process nodes to the list of consumers
                    if( node.process != null )
                        consumers << node.process.name
                    // add operator nodes to the queue to keep searching
                    else
                        queue << node
                }
            }

            log.trace "Process `${processName}` is consumed by the following processes: ${consumers}"

            processes[processName] = new ProcessState(consumers ?: [processName] as Set)
        }
    }

    static private final Set<Mode> INVALID_PUBLISH_MODES = [Mode.COPY_NO_FOLLOW, Mode.RELLINK, Mode.SYMLINK]

    /**
     * Log warning for any process that uses any incompatible features.
     *
     * @param process
     */
    void onProcessCreate( TaskProcessor process ) {
        // check for includeInputs
        final outputs = process.config.getOutputs()

        if( outputs.any( p -> p instanceof FileOutParam && p.includeInputs ) )
            log.warn "Process `${process.name}` is forwarding input files with includeInputs, which may be invalidated by eager cleanup"

        // check for incompatible publish modes
        final taskConfig = process.getPreviewConfig()
        final publishDirs = taskConfig.getPublishDir()

        if( publishDirs.any( p -> p.mode in INVALID_PUBLISH_MODES ) )
            log.warn "Process `${process.name}` is publishing files as symlinks, which may be invalidated by eager cleanup -- consider using 'copy' or 'link' instead"
    }

    /**
     * When a task is created, add it to the state map and add it as a consumer
     * of any upstream tasks and output files.
     *
     * @param handler
     * @param trace
     */
    @Override
    void onProcessPending(TaskHandler handler, TraceRecord trace) {
        // query task input files
        final task = handler.task
        final inputs = task.getInputFilesMap().values()

        sync.lock()
        try {
            // add task to the task state map
            tasks[task] = new TaskState()

            // add task as consumer of each upstream task and output file
            for( Path path : inputs ) {
                if( path in paths ) {
                    final pathState = paths[path]
                    final taskState = tasks[pathState.task]
                    taskState.consumers << task
                    pathState.consumers << task
                }
            }
        }
        finally {
            sync.unlock()
        }
    }

    /**
     * When a task is completed, track the task and its output files
     * for automatic cleanup.
     *
     * @param handler
     * @param trace
     */
    @Override
    void onProcessComplete(TaskHandler handler, TraceRecord trace) {
        final task = handler.task

        // handle failed tasks separately
        if( !task.isSuccess() ) {
            handleTaskFailure(task)
            return
        }

        // query task output files
        final outputs = task
            .getOutputsByType(FileOutParam)
            .values()
            .flatten() as List<Path>

        // get publish outputs
        final publishDirs = task.config.getPublishDir()
        final publishOutputs = publishDirs
            ? outputs.findAll( p -> publishDirs.any( publishDir -> publishDir.canPublish(p, task) ) )
            : []

        log.trace "Task ${task.name} will publish the following files: ${publishOutputs*.toUriString()}"

        sync.lock()
        try {
            // mark task as completed
            tasks[task].completed = true

            // remove any outputs have already been published
            final alreadyPublished = publishedOutputs.intersect(publishOutputs)
            publishedOutputs.removeAll(alreadyPublished)
            publishOutputs.removeAll(alreadyPublished)

            // add publish outputs to wait on
            tasks[task].publishOutputs = publishOutputs as Set<Path>

            // scan tasks for cleanup
            cleanup0()

            // add each output file to the path state map
            for( Path path : outputs ) {
                final pathState = new PathState(task)
                if( path !in publishOutputs )
                    pathState.published = true

                paths[path] = pathState
            }
        }
        finally {
            sync.unlock()
        }
    }

    /**
     * When a task fails, mark it as completed without tracking its
     * output files. Failed tasks are not included as consumers of
     * upstream tasks in the cache.
     *
     * @param task
     */
    void handleTaskFailure(TaskRun task) {
        sync.lock()
        try {
            // mark task as completed
            tasks[task].completed = true
        }
        finally {
            sync.unlock()
        }
    }

    /**
     * When a file is published, mark it as published and check
     * the corresponding task for cleanup.
     *
     * If the file is published before the corresponding task is
     * marked as completed, save it for later.
     *
     * @param destination
     * @param source
     */
    @Override
    void onFilePublish(Path destination, Path source) {
        sync.lock()
        try {
            // get the corresponding task
            final pathState = paths[source]
            if( pathState ) {
                final task = pathState.task

                log.trace "File ${source.toUriString()} was published by task ${task.name}"

                // mark file as published
                tasks[task].publishOutputs.remove(source)
                pathState.published = true

                // delete task if it can be deleted
                if( canDeleteTask(task) )
                    deleteTask(task)
                else if( canDeleteFile(source) )
                    deleteFile(source)
            }
            else {
                log.trace "File ${source.toUriString()} was published before task was marked as completed"

                // save file to be processed when task completes
                publishedOutputs << source
            }
        }
        finally {
            sync.unlock()
        }
    }

    /**
     * When a process is closed (all tasks of the process have been created),
     * mark the process as closed and scan tasks for cleanup.
     *
     * @param process
     */
    @Override
    void onProcessClose(TaskProcessor process) {
        sync.lock()
        try {
            processes[process.name].closed = true
            cleanup0()
        }
        finally {
            sync.unlock()
        }
    }

    /**
     * Delete any task directories and output files that can be deleted.
     */
    private void cleanup0() {
        for( TaskRun task : tasks.keySet() )
            if( canDeleteTask(task) )
                deleteTask(task)

        for( Path path : paths.keySet() )
            if( canDeleteFile(path) )
                deleteFile(path)
    }

    /**
     * Determine whether a task directory can be deleted.
     *
     * A task directory can be deleted if:
     * - the task has completed
     * - the task directory hasn't already been deleted
     * - all of its publish outputs have been published
     * - all of its process consumers are closed
     * - all of its task consumers are completed
     *
     * @param task
     */
    private boolean canDeleteTask(TaskRun task) {
        final taskState = tasks[task]
        final processState = processes[task.processor.name]

        taskState.completed
            && !taskState.deleted
            && taskState.publishOutputs.isEmpty()
            && processState.consumers.every( p -> processes[p].closed )
            && taskState.consumers.every( t -> tasks[t].completed )
    }

    /**
     * Delete a task directory.
     *
     * @param task
     */
    private void deleteTask(TaskRun task) {
        log.trace "Deleting task directory: ${task.workDir.toUriString()}"

        // delete task
        final taskState = tasks[task]
        FileHelper.deletePath(task.workDir)
        taskState.deleted = true

        // finalize task in the cache db
        final consumers = taskState.consumers
            .findAll( t -> t.isSuccess() )
            .collect( t -> t.hash )
        cache.finalizeTaskAsync(task.hash, consumers)
    }

    /**
     * Determine whether a file can be deleted.
     *
     * A file can be deleted if:
     * - the file has been published (or doesn't need to be published)
     * - the file hasn't already been deleted
     * - all of its process consumers are closed
     * - all of its task consumers are completed
     *
     * @param path
     */
    private boolean canDeleteFile(Path path) {
        final pathState = paths[path]
        final processState = processes[pathState.task.processor.name]

        pathState.published
            && !pathState.deleted
            && processState.consumers.every( p -> processes[p].closed )
            && pathState.consumers.every( t -> tasks[t].completed )
    }

    /**
     * Delete a file.
     *
     * @param path
     */
    private void deleteFile(Path path) {
        log.trace "Deleting file: ${path.toUriString()}"

        final pathState = paths[path]
        final taskState = tasks[pathState.task]
        if( !taskState.deleted )
            FileHelper.deletePath(path)
        pathState.deleted = true
    }

    static private class ProcessState {
        Set<String> consumers
        boolean closed = false

        ProcessState(Set<String> consumers) {
            this.consumers = consumers
        }
    }

    static private class TaskState {
        Set<TaskRun> consumers = []
        Set<Path> publishOutputs = []
        boolean completed = false
        boolean deleted = false
    }

    static private class PathState {
        TaskRun task
        Set<TaskRun> consumers = []
        boolean deleted = false
        boolean published = false

        PathState(TaskRun task) {
            this.task = task
        }
    }

}
