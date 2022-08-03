/*
 * Copyright 2020-2022, Seqera Labs
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
 *
 */

package nextflow.cloud.azure.file

import groovy.transform.Memoized
import nextflow.cloud.azure.config.AzCopyOpts
import nextflow.executor.BashFunLib
import nextflow.util.Duration

/**
 * Azure Bash helper functions
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
class AzBashLib extends BashFunLib<AzBashLib> {

    private String blockSize = AzCopyOpts.DEFAULT_BLOCK_SIZE
    private String blobTier = AzCopyOpts.DEFAULT_BLOB_TIER
    private String putMD5 = ''
    private String checkMD5 = AzCopyOpts.DEFAULT_CHECK_MD5
    private String overwrite = AzCopyOpts.DEFAULT_OVERWRITE
    private String outputLevel = AzCopyOpts.DEFAULT_OUTPUT_LEVEL


    AzBashLib withBlockSize(String value) {
        if (value)
            this.blockSize = value
        return this
    }

    AzBashLib withBlobTier(String value) {
        if (value)
            this.blobTier = value
        return this
    }

    AzBashLib withPutMD5(Boolean value) {
        this.putMD5 = value ? '--put-md5' : ''
        return this
    }

    AzBashLib withCheckMD5(String value) {
        if (value)
            this.checkMD5 = value
        return this
    }

    AzBashLib withOverwrite(String value) {
        if (value)
            this.overwrite = value
        return this
    }

    AzBashLib withOutputLevel(String value) {
        if (value)
            this.outputLevel = value
        return this
    }

    protected String azLib() {
        """
        nxf_az_upload() {
            local name=\$1
            local target=\${2%/} ## remove ending slash
            local base_name="\$(basename "\$name")"
            local dir_name="\$(dirname "\$name")"

            if [[ -d \$name ]]; then
              if [[ "\$base_name" == "\$name" ]]; then
                azcopy cp "\$name" "\$target?\$AZ_SAS" --recursive --block-blob-tier ${blobTier} --block-size-mb ${blockSize} --output-level ${outputLevel} ${putMD5}
              else
                azcopy cp "\$name" "\$target/\$dir_name?\$AZ_SAS " --recursive ${blobTier} ${blockSize} --output-level ${outputLevel} ${putMD5}
              fi
            else
              azcopy cp "\$name" "\$target/\$name?\$AZ_SAS" ${blobTier} ${blockSize} --output-level ${outputLevel} ${putMD5}
            fi
        }
        
        nxf_az_download() {
            local source=\$1
            local target=\$2
            local basedir=\$(dirname \$2)
            local ret
            mkdir -p "\$basedir"
        
            ret=\$(azcopy cp "\$source?\$AZ_SAS" "\$target" 2>&1) || {
                ## if fails check if it was trying to download a directory
                mkdir -p \$target
                azcopy cp "\$source/*?\$AZ_SAS" "\$target" --recursive --output-level ${outputLevel} --check-md5 ${checkMD5} --overwrite ${overwrite} >/dev/null || {
                    rm -rf \$target
                    >&2 echo "Unable to download path: \$source"
                    exit 1
                }
            }
        }
        """.stripIndent()
    }

    String render() {
        super.render() + azLib()
    }

    @Memoized
    static String script(AzCopyOpts opts, Integer maxParallelTransfers, Integer maxTransferAttempts, Duration delayBetweenAttempts) {
        new AzBashLib()
                .includeCoreFun(true)
                .withMaxParallelTransfers(maxParallelTransfers)
                .withMaxTransferAttempts(maxTransferAttempts)
                .withDelayBetweenAttempts(delayBetweenAttempts)
                .withBlobTier(opts.blobTier)
                .withBlockSize(opts.blockSize)
                .withPutMD5(opts.putMD5)
                .withCheckMD5(opts.checkMD5)
                .withOverwrite(opts.overwrite)
                .withOutputLevel(opts.outputLevel)
                .render()
    }

    @Memoized
    static String script() {
        new AzBashLib().render()
    }
}
