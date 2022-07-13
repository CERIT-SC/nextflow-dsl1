/*
 * Copyright 2022, Google Inc.
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

package nextflow.cloud.google.batch.model

import groovy.transform.CompileStatic
import groovy.transform.ToString

/**
 * Model a Batch Task Group
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@CompileStatic
@ToString(includeNames = true, ignoreNulls = true, includePackage = false)
class TaskGroup {

    // Output only. TaskGroup name.
    // The system generates this field based on parent Job name.
    // For example:
    // "projects/123456/locations/us-west1/jobs/job01/taskGroups/default-group".
    String name


    /**
     *  Required. Tasks in the group share the same task spec.
     */
    TaskSpec taskSpec

    /**
     *  Number of Tasks in the TaskGroup.  default is 1
     */
    Integer taskCount

    /**
     * Max number of tasks that can run in parallel.
     * Default to min(task_count, 1000).
     */
    Integer parallelism

    /**
     // An array of environment variable mappings, which are passed to Tasks with
     // matching indices. If task_environments is used then task_count should
     // not be specified in the request (and will be ignored). Task count will be
     // the length of task_environments.
     //
     // Tasks get a BATCH_TASK_INDEX and BATCH_TASK_COUNT environment variable, in
     // addition to any environment variables set in task_environments, specifying
     // the number of Tasks in the Task's parent TaskGroup, and the specific Task's
     // index in the TaskGroup (0 through BATCH_TASK_COUNT - 1).
     //
     // task_environments supports up to 200 entries.
     */
    List<Environment> taskEnvironments

    /**
     * Max number of tasks that can be run on a node
     * at the same time. Default is 1.
     */
    Integer taskCountPerNode

    /**
     *  When true, Batch will populate a file with a list of all VMs assigned to
     * the TaskGroup and set the BATCH_HOSTS_FILE environment variable to the path
     * of that file. Defaults to false.
     */
    Boolean requireHostsFile

    // When true, Batch will configure SSH to allow passwordless login between
    // VMs for the user running the Batch tasks.
    Boolean permissiveSsh

    TaskGroup withTaskSpec(TaskSpec spec) {
        this.taskSpec = spec
        return this
    }

}