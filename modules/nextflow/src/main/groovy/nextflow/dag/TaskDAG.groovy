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

package nextflow.dag

import java.nio.file.Path
import java.util.concurrent.locks.Lock
import java.util.concurrent.locks.ReentrantLock

import groovy.transform.CompileStatic
import groovy.transform.TupleConstructor
import groovy.util.logging.Slf4j
import nextflow.processor.TaskRun
import nextflow.script.params.FileOutParam
/**
 * Model the task graph of a pipeline execution.
 *
 * @author Ben Sherman <bentshermann@gmail.com>
 */
@Slf4j
@CompileStatic
class TaskDAG {

    private Map<TaskRun,Vertex> vertices = new HashMap<>()

    private Map<Path,TaskRun> taskLookup = new HashMap<>()

    private Lock sync = new ReentrantLock()

    Map<TaskRun,Vertex> getVertices() { vertices }

    /**
     * Add a task to the graph.
     *
     * @param task
     */
    void addTask(TaskRun task) {
        final hash = task.hash.toString()
        final label = "[${hash.substring(0,2)}/${hash.substring(2,8)}] ${task.name}"
        final inputs = task.getInputFilesMap()

        sync.lock()
        try {
            // add new task to graph
            vertices[task] = new Vertex(vertices.size(), label, inputs)
        }
        finally {
            sync.unlock()
        }
    }

    /**
     * Add a task's outputs to the graph.
     *
     * @param task
     */
    void addTaskOutputs(TaskRun task) {
        final outputs = task
            .getOutputsByType(FileOutParam)
            .values()
            .flatten() as Set<Path>

        sync.lock()
        try {
            // add task outputs to graph
            vertices[task].outputs = outputs

            // add new output files to task lookup
            for( Path path : outputs )
                taskLookup[path] = task
        }
        finally {
            sync.unlock()
        }
    }

    /**
     * Get the task that produced the given file.
     *
     * @param path
     */
    TaskRun getProducerTask(Path path) {
        taskLookup[path]
    }

    /**
     * Get the vertex for the task that produced the given file.
     *
     * @param path
     */
    Vertex getProducerVertex(Path path) {
        vertices[taskLookup[path]]
    }

    @TupleConstructor(excludes = 'outputs')
    static class Vertex {
        int index
        String label
        Map<String,Path> inputs
        Set<Path> outputs

        String getSlug() { "t${index}" }

        @Override
        String toString() { label }
    }

}
