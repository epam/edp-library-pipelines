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

package com.epam.edp

import com.epam.edp.platform.Platform
import groovy.json.JsonSlurperClassic

class GitInfo {
    Script script
    Platform platform
    Job job

    static final String GIT_SERVER_PLURAL_NAME = "gitservers"
    static final String CODEBASE_PLURAL_NAME = "codebases"

    def credentialsId
    def autouser
    def host
    def project
    def branch
    def changeNumber = 0
    def changeName
    def refspecName
    def sshPort
    def patchsetNumber = 0
    def repositoryRelativePath
    def gitServerCrName
    def gitServerCrVersion
    def gitServerCrApiGroup
    def codebaseCrApiGroup

    GitInfo(job, platform, script) {
        this.script = script
        this.platform = platform
        this.job = job
    }

    def init() {
        this.gitServerCrName = job.getParameterValue("GIT_SERVER_CR_NAME")
        this.gitServerCrVersion = job.crApiVersion
        this.gitServerCrApiGroup = "${GIT_SERVER_PLURAL_NAME}.${gitServerCrVersion}.${job.EDP_EPAM_COM_POSTFIX}"
        this.codebaseCrApiGroup = "${CODEBASE_PLURAL_NAME}.${gitServerCrVersion}.${job.EDP_EPAM_COM_POSTFIX}"

        script.println("[JENKINS][DEBUG] Git Server CR Name: ${gitServerCrName}")
        script.println("[JENKINS][DEBUG] Git Server CR Version: ${gitServerCrVersion}")
        script.println("[JENKINS][DEBUG] Git Server CR API Group: ${gitServerCrApiGroup}")

        this.credentialsId = platform.getJsonPathValue(gitServerCrApiGroup, gitServerCrName, ".spec.nameSshKeySecret")
        this.autouser = platform.getJsonPathValue(gitServerCrApiGroup, gitServerCrName, ".spec.gitUser")
        this.host = platform.getJsonPathValue(gitServerCrApiGroup, gitServerCrName, ".spec.gitHost")
        this.sshPort = platform.getJsonPathValue(gitServerCrApiGroup, gitServerCrName, ".spec.sshPort")

        script.println("[JENKINS][DEBUG] credentialsId: ${this.credentialsId}")
        script.println("[JENKINS][DEBUG] autouser: ${this.autouser}")
        script.println("[JENKINS][DEBUG] host: ${this.host}")
        script.println("[JENKINS][DEBUG] sshPort: ${this.sshPort}")

        this.project = job.getParameterValue("GERRIT_PROJECT")
        this.branch = job.getParameterValue("GERRIT_BRANCH")
        if (!this.branch)
            this.branch = job.getParameterValue("BRANCH", "master")
        this.patchsetNumber = job.getParameterValue("GERRIT_PATCHSET_NUMBER")
        this.changeNumber = job.getParameterValue("GERRIT_CHANGE_NUMBER")
        this.changeName = "change-${this.changeNumber}-${this.patchsetNumber}"
        this.refspecName = job.getParameterValue("GERRIT_REFSPEC")
        switch (job.type) {
            case [JobType.BUILD.value, JobType.CODEREVIEW.value]:
                if (this.project == null)
                    this.project = job.getParameterValue("GERRIT_PROJECT_NAME")
                if (this.project == null)
                    script.error("[JENKINS][ERROR] Couldn't determine project, please make sure that GERRIT_PROJECT_NAME variable is defined")
        }

        def strategy = platform.getJsonPathValue(codebaseCrApiGroup, this.project, ".spec.strategy")
        if (strategy == "import") {
            this.repositoryRelativePath = platform.getJsonPathValue(codebaseCrApiGroup, this.project, ".spec.gitUrlPath")
        }

    }
}