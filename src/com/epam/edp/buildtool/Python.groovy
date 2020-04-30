/* Copyright 2018 EPAM Systems.

 Licensed under the Apache License, Version 2.0 (the "License");
 you may not use this file except in compliance with the License.
 You may obtain a copy of the License at
 http://www.apache.org/licenses/LICENSE-2.0

 Unless required by applicable law or agreed to in writing, software
 distributed under the License is distributed on an "AS IS" BASIS,
 WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.

 See the License for the specific language governing permissions and
 limitations under the License.*/

package com.epam.edp.buildtool

import com.epam.edp.Job
import com.epam.edp.Nexus
import org.apache.commons.lang.RandomStringUtils

class Python implements BuildTool {
    Script script
    Nexus nexus
    Job job

    def groupRepository
    def hostedRepository
    def snapshotsPath
    def releasesPath
    def groupPath
    def hostedPath

    def init() {
        this.snapshotsPath = job.getParameterValue("ARTIFACTS_SNAPSHOTS_PATH", "edp-python-snapshots")
        this.releasesPath = job.getParameterValue("ARTIFACTS_RELEASES_PATH", "edp-python-releases")
        this.hostedPath = job.getParameterValue("ARTIFACTS_HOSTED_PATH", "edp-python")
        this.groupPath = job.getParameterValue("ARTIFACTS_PUBLIC_PATH", "edp-python-group")
        this.groupRepository = "${nexus.repositoriesUrl}/${this.groupPath}"
        this.hostedRepository = "${nexus.repositoriesUrl}/${this.hostedPath}"
    }
}
