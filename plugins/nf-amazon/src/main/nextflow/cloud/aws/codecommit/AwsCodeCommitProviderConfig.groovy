/*
 * Copyright 2020, Seqera Labs
 * Copyright 2013-2019, Centre for Genomic Regulation (CRG)
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

package nextflow.cloud.aws.codecommit

import nextflow.scm.ProviderConfig

/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
class AwsCodeCommitProviderConfig extends ProviderConfig {

    String region

    AwsCodeCommitProviderConfig(String host) {
        super('codecommit', [platform:'codecommit', server: "https://$host/v1"])
        assert host =~ /git-codecommit\.[a-z0-9-]+\.amazonaws\.com/, "Invalid AWS CodeCommit host name: $host"
        this.region = host.tokenize('.')[1]
    }

    @Override
    protected String resolveProjectName(String path) {
        assert path
        assert !path.startsWith('/')
        return "codecommit/$region/" + path.tokenize('/')[-1]
    }

    String toString() {
        "AwsCodeCommitProviderConfig[name=$name; region=$region; platform=$platform; server=$server]"
    }

}
