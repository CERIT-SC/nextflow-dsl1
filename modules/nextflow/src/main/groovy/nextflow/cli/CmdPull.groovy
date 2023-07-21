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

package nextflow.cli
import com.beust.jcommander.Parameter
import com.beust.jcommander.Parameters
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import nextflow.exception.AbortOperationException
import nextflow.plugin.Plugins
import nextflow.scm.AssetManager
/**
 * CLI sub-command PULL
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Slf4j
@CompileStatic
class CmdPull {

    interface Options extends IHubOptions {
        String getPipeline()
        boolean getAll()
        Integer getDeep()
        String getRevision()
    }

    @Parameters(commandDescription = 'Download or update a project')
    static class V1 extends CmdBase implements Options, HubOptions {

        @Parameter(description = 'project name or repository url to pull', arity = 1)
        List<String> args

        @Parameter(names='-all', description = 'Update all downloaded projects', arity = 0)
        boolean all

        @Parameter(names=['-r','-revision'], description = 'Revision of the project to run (either a git branch, tag or commit SHA number)')
        String revision

        @Parameter(names=['-d','-deep'], description = 'Create a shallow clone of the specified depth')
        Integer deep

        @Override
        String getPipeline() { args[0] }

        @Override
        String getName() { 'pull' }

        @Override
        void run() {
            new CmdPull(this).run()
        }

    }

    @Delegate
    private Options options

    /* only for testing purpose */
    protected File root

    CmdPull(Options options) {
        this.options = options
    }

    void run() {

        if( !pipeline && !all )
            throw new AbortOperationException('Project name or option `-all` is required')

        def list = all ? AssetManager.list() : [pipeline]
        if( !list ) {
            log.info "(nothing to do)"
            return
        }

        /* only for testing purpose */
        if( root ) {
            AssetManager.root = root
        }

        // init plugin system
        Plugins.init()

        list.each {
            log.info "Checking $it ..."
            def manager = new AssetManager(it, this)

            def result = manager.download(revision,deep)
            manager.updateModules()

            def scriptFile = manager.getScriptFile()
            String message = !result ? " done" : " $result"
            message += " - revision: ${scriptFile.revisionInfo}"
            log.info message
        }

    }

}
