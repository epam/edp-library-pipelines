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

class Npm implements BuildTool {
    Script script
    Nexus nexus
    Job job

    def settings
    def groupRepository
    def hostedRepository
    def groupPath
    def hostedPath
    def releaseRepository
    def snapshotRepository
    def releasePath
    def snapshotPath


    def init() {
        this.groupPath = job.getParameterValue("ARTIFACTS_PUBLIC_PATH", "edp-npm-group")
        this.hostedPath = job.getParameterValue("ARTIFACTS_HOSTED_PATH", "edp-npm-hosted")
        this.releasePath = job.getParameterValue("ARTIFACTS_RELEASE_PATH", "edp-npm-releases")
        this.snapshotPath = job.getParameterValue("ARTIFACTS_SNAPSHOT_PATH", "edp-npm-snapshots")
        this.hostedRepository = "${nexus.repositoriesUrl}/${hostedPath}/"
        this.groupRepository = "${nexus.repositoriesUrl}/${groupPath}/"
        this.releaseRepository = "${nexus.repositoriesUrl}/${releasePath}/"
        this.snapshotRepository = "${nexus.repositoriesUrl}/${snapshotPath}/"
    }
}