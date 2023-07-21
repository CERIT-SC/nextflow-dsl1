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

package nextflow.cli.v2

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import nextflow.cli.ILauncherOptions
import nextflow.cli.CmdRun
import nextflow.util.Duration
import picocli.CommandLine.Command
import picocli.CommandLine.ITypeConverter
import picocli.CommandLine.Option
import picocli.CommandLine.Parameters
import picocli.CommandLine.ParentCommand

/**
 * CLI `run` sub-command (v2)
 *
 * @author Ben Sherman <bentshermann@gmail.com>
 */
@Slf4j
@CompileStatic
@Command(
    name = 'run',
    description = 'Execute a pipeline'
)
class RunCmd extends AbstractCmd implements CmdRun.Options, HubOptions {

    static class DurationConverter implements ITypeConverter<Long> {
        @Override
        Long convert(String value) {
            if( !value ) throw new IllegalArgumentException()
            if( value.isLong() ) { return value.toLong() }
            return Duration.of(value).toMillis()
        }
    }

    @ParentCommand
    private Launcher launcher

    @Parameters(index = '0', description = 'Project name or repository url')
    String pipeline

    @Parameters(index = '1..*', description = 'Pipeline script args')
    List<String> args = []

    @Option(names = ['--ansi-log'], arity = '1', paramLabel = 'true|false', description = 'Use ANSI logging')
    void setAnsiLog(boolean value) {
        launcher.options.ansiLog = value
    }

    @Option(names = ['--bg'], arity = '0', description = 'Run as a background process')
    void setBackground(boolean value) {
        launcher.options.background = value
    }

    @Option(names = ['--bucket-dir'], paramLabel = '<bucket>', description = 'Remote bucket where intermediate result files are stored')
    String bucketDir

    @Option(names = ['--cache'], arity = '1', paramLabel = 'true|false', description = 'Enable/disable process caching')
    Boolean cacheable

    @Option(names = ['--cluster.'], arity = '0..1', fallbackValue = 'true', description = 'Set cluster options', hidden = true)
    Map<String,String> clusterOptions = [:]

    @Option(names = ['-c','--config'], split = ',', hidden = true)
    List<String> runConfig

    @Option(names=['-d','--deep'], description = 'Create a shallow clone of the specified depth')
    Integer deep

    @Option(names = ['--disable-jobs-cancellation'], description = 'Do not cancel child jobs on workflow termination')
    Boolean disableJobsCancellation

    @Option(names = ['--dsl1'], description = 'Execute the workflow using DSL1 syntax (no longer supported)')
    boolean dsl1

    @Option(names = ['--dsl2'], description = 'Execute the workflow using DSL2 syntax')
    boolean dsl2

    @Option(names = ['--dump-channels'], arity = '0..1', fallbackValue = '*', paramLabel = '<channels>', description = 'Dump channels for debugging purposes')
    String dumpChannels

    @Option(names = ['--dump-hashes'], description = 'Dump task hash keys for debugging purposes')
    boolean dumpHashes

    @Option(names = ['--entry'], arity = '1', paramLabel = '<workflow>', description = 'Entry workflow name to be executed')
    String entryName

    @Option(names = ['-e.','--env.'], paramLabel = '<name>=<value>', description = 'Add the specified variable to execution environment')
    Map<String,String> env = [:]

    @Option(names = ['--executor.'], arity = '0..1', fallbackValue = 'true', paramLabel = '<name>[=<value>]', description = 'Set executor options', hidden = true)
    Map<String,String> executorOptions = [:]

    @Option(names = ['-E','--export-sys-env'], description = 'Export the current system environment')
    boolean exportSysEnv

    @Option(names = ['--latest'], description = 'Pull latest changes before run')
    boolean latest

    @Option(names = ['--lib'], paramLabel = '<path>', description = 'Library extension path')
    String libPath

    @Option(names = ['--main-script'], paramLabel = '<file>', description = 'The script file to be executed when launching a project directory or repository' )
    String mainScript

    @Option(names = ['--name'], paramLabel = '<name>', description = 'Assign a mnemonic name to the pipeline run')
    String runName

    @Option(names = ['--offline'], description = 'Do not check for remote project updates')
    boolean offline

    @Option(names = ['--params-file'], paramLabel = '<file>', description = 'Load script parameters from a JSON/YAML file')
    String paramsFile

    @Option(names = ['--plugins'], description = 'Specify the plugins to be applied for this run e.g. nf-amazon,nf-tower')
    String plugins

    @Option(names = ['--poll-interval'], paramLabel = '<value>', description = 'Executor poll interval (duration string ending with ms|s|m)', converter = DurationConverter, hidden = true)
    long pollInterval

    @Option(names = ['--pool-size'], paramLabel = '<value>', description = 'Number of threads in the execution pool', hidden = true)
    Integer poolSize

    @Option(names = ['--preview'], description = 'Run the workflow script skipping the execution of all processes')
    boolean preview

    @Option(names = ['--process.'], arity = '0..1', fallbackValue = 'true', paramLabel = '<name>[=<value>]', description = 'Set process options' )
    Map<String,String> processOptions = [:]

    @Option(names = ['--profile'], description = 'Use a configuration profile')
    String profile

    @Option(names = ['--queue-size'], paramLabel = '<value>', description = 'Max number of processes that can be executed in parallel by each executor')
    Integer queueSize

    @Option(names = ['--resume'], arity = '0..1', fallbackValue = 'last', description = 'Execute the script using the cached results, useful to continue executions that was stopped by an error')
    String resume

    @Option(names = ['-r','--revision'], description = 'Revision of the project to run (either a git branch, tag or commit SHA number)')
    String revision

    @Option(names = ['--stub','--stub-run'], description = 'Execute the workflow replacing process scripts with command stubs')
    boolean stubRun

    @Option(names = ['--test'], arity = '0..1', fallbackValue = '%all', paramLabel = '<name>', description = 'Test a script function with the name specified')
    String test

    @Option(names = ['-w','--work-dir'], paramLabel = '<path>', description = 'Directory where intermediate result files are stored')
    String workDir

    @Option(names = ['--with-apptainer'], arity = '0..1', fallbackValue = '-', paramLabel = '<container>', description = 'Enable process execution in an Apptainer container')
    String withApptainer

    @Option(names = ['--with-charliecloud'], arity = '0..1', fallbackValue = '-', paramLabel = '<container>', description = 'Enable process execution in a Charliecloud container runtime')
    String withCharliecloud

    @Option(names = ['--with-conda'], arity = '0..1', fallbackValue = '-', paramLabel = '<name>|<file>', description = 'Use the specified Conda environment, package, or file (must end with .yml|.yaml suffix)')
    String withConda

    @Option(names = ['--without-conda'], arity = '0', description = 'Disable the use of Conda environments')
    Boolean withoutConda

    @Option(names = ['--with-dag'], arity = '0..1', fallbackValue = '-', paramLabel = '<filename>', description = 'Create pipeline DAG file')
    String withDag

    @Option(names = ['--with-docker'], arity = '0..1', fallbackValue = '-', paramLabel = '<container>', description = 'Enable process execution in a Docker container')
    String withDocker

    @Option(names = ['--without-docker'], arity = '0', description = 'Disable process execution with Docker')
    boolean withoutDocker

    @Option(names = ['--with-fusion'], hidden = true)
    String withFusion

    @Option(names = ['--with-mpi'], hidden = true)
    boolean withMpi

    @Option(names = ['-N','--with-notification'], arity = '0..1', fallbackValue = '-', paramLabel = '<recipients>', description = 'Send a notification email on workflow completion to the specified recipients')
    String withNotification

    @Option(names = ['--with-podman'], arity = '0..1', fallbackValue = '-', paramLabel = '<container>', description = 'Enable process execution in a Podman container')
    String withPodman

    @Option(names = ['--without-podman'], arity = '0', description = 'Disable process execution in a Podman container')
    boolean withoutPodman

    @Option(names = ['--with-report'], arity = '0..1', fallbackValue = '-', paramLabel = '<filename>', description = 'Create processes execution html report')
    String withReport

    @Option(names = ['--with-singularity'], arity = '0..1', fallbackValue = '-', paramLabel = '<container>', description = 'Enable process execution in a Singularity container')
    String withSingularity

    @Option(names = ['--with-spack'], arity = '0..1', fallbackValue = '-', paramLabel = '<name>|<file>', description = 'Use the specified Spack environment, package, or file (must end with .yaml suffix)')
    String withSpack

    @Option(names = ['--without-spack'], arity = '0', description = 'Disable the use of Spack environments')
    Boolean withoutSpack

    @Option(names = ['--with-timeline'], arity = '0..1', fallbackValue = '-', paramLabel = '<filename>', description = 'Create processes execution timeline file')
    String withTimeline

    @Option(names = ['--with-tower'], arity = '0..1', fallbackValue = '-', paramLabel = '<url>', description = 'Monitor workflow execution with Seqera Tower service')
    String withTower

    @Option(names = ['--with-trace'], arity = '0..1', fallbackValue = '-', paramLabel = '<filename>', description = 'Create processes execution tracing file')
    String withTrace

    @Option(names = ['--with-wave'], arity = '0..1', fallbackValue = '-', paramLabel = '<url>', description = 'Enable dynamic container provisioning with Seqera Wave service', hidden = true)
    String withWave

    @Option(names = ['--with-weblog'], arity = '0..1', fallbackValue = '-', paramLabel = '<url>', description = 'Send workflow status messages via HTTP to target URL')
    String withWebLog

    private List<String> pipelineArgs = null

    private Map<String,String> pipelineParams = null

    /**
     * Parse the pipeline args and params from the positional
     * args parsed by picocli. This method assumes that the first
     * positional arg that starts with '--' is the first param,
     * and parses the remaining args as params.
     *
     * NOTE: While the double-dash ('--') notation can be used to
     * distinguish pipeline params from CLI options, it cannot be
     * used to distinguish pipeline params from pipeline args.
     */
    private void parseArgs() {
        // parse pipeline args
        int i = args.findIndexOf { it.startsWith('--') }
        pipelineArgs = i == -1 ? args : args[0..<i]

        // parse pipeline params
        pipelineParams = [:]

        if( i == -1 )
            return

        while( i < args.size() ) {
            String current = args[i++]
            if( !current.startsWith('--') ) {
                throw new IllegalArgumentException("Invalid argument '${current}' -- unable to parse it as a pipeline arg, pipeline param, or CLI option")
            }

            String key
            String value

            // parse '--param=value'
            if( current.contains('=') ) {
                int split = current.indexOf('=')
                key = current.substring(2, split)
                value = current.substring(split+1)
            }

            // parse '--param value'
            else if( i < args.size() && !args[i].startsWith('--') ) {
                key = current.substring(2)
                value = args[i++]
            }

            // parse '--param1 --param2 ...' as '--param1 true --param2 ...'
            else {
                key = current.substring(2)
                value = 'true'
            }

            pipelineParams.put(key, value)
        }

        log.trace "Parsing pipeline args from CLI: $pipelineArgs"
        log.trace "Parsing pipeline params from CLI: $pipelineParams"
    }

    /**
     * Get the list of pipeline args.
     */
    @Override
    List<String> getArgs() {
        if( pipelineArgs == null ) {
            parseArgs()
        }

        return pipelineArgs
    }

    /**
     * Get the map of pipeline params.
     */
    @Override
    Map<String,String> getParams() {
        if( pipelineParams == null ) {
            parseArgs()
        }

        return pipelineParams
    }

    @Override
    String getLauncherCliString() {
        launcher.cliString
    }

    @Override
    ILauncherOptions getLauncherOptions() {
        launcher.options
    }

    @Override
    void run() {
        new CmdRun(this).run()
    }

}
