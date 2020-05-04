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
    def displayBranch
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

        this.project = defineVariable(["GERRIT_PROJECT", "GERRIT_PROJECT_NAME"])
        this.branch = defineVariable(["GERRIT_BRANCH", "BRANCH", "ghprbActualCommit", "gitlabMergeRequestLastCommit", "COMMIT_ID"])
        this.displayBranch = defineVariable(["GERRIT_BRANCH", "ghprbSourceBranch", "ghprbSourceBranch", "BRANCH"])

        switch (job.type) {
            case JobType.CODEREVIEW.value:
                this.changeNumber = defineVariable(["GERRIT_CHANGE_NUMBER", "ghprbPullId", "gitlabMergeRequestIid"])
                this.patchsetNumber = job.getParameterValue("GERRIT_PATCHSET_NUMBER")
                this.refspecName = job.getParameterValue("GERRIT_REFSPEC")
                if (this.patchsetNumber && this.changeNumber)
                    this.changeName = "change-${this.changeNumber}-${this.patchsetNumber}"

                if (!this.changeName)
                    this.changeName = job.getParameterValue("ghprbPullId") ? "pr-${this.changeNumber}" : "mr-${this.changeNumber}"
                break
            case JobType.CREATERELEASE.value:
                this.branch = this.branch ?: "master"
                break
        }
        script.println("[JENKINS][DEBUG]\r\nproject = ${project}\r\nbranch = ${branch}\r\ndisplayBranch = ${displayBranch}")

        if (!this.project)
            script.error("[JENKINS][ERROR] Couldn't determine project, please make sure that GERRIT_PROJECT_NAME variable is defined")
        if (!this.branch)
            script.error("[JENKINS][ERROR] Couldn't determine branch to clone, please make sure that BRANCH variable is defined")

        def strategy = platform.getJsonPathValue(codebaseCrApiGroup, this.project, ".spec.strategy")
        if (strategy == "import") {
            this.repositoryRelativePath = platform.getJsonPathValue(codebaseCrApiGroup, this.project, ".spec.gitUrlPath")
        }
    }

    def defineVariable(envVariablesList) {
        def variable = null
        for (env in envVariablesList) {
            variable = this.job.getParameterValue("${env}")
            if (variable)
                return variable
        }
        return variable
    }
}