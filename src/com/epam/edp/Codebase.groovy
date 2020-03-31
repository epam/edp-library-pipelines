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

import groovy.json.JsonSlurperClassic
import com.epam.edp.platform.Platform

class Codebase {
    Script script
    Platform platform
    Job job

    def name
    def config = [:]
    def version = ""
    def deployableModule = ""
    def buildVersion = ""
    def deployableModuleDir = ""
    def imageBuildArgs = []
    def gitServerCrName = ""
    def branchVersion = ""
    def currentBuildNumber = ""
    def isReleaseBranch = false
    def vcsTag = ""
    def isTag = ""

    Codebase(job, name, platform, script) {
        this.job = job
        this.name = name
        this.script = script
        this.platform = platform
    }


    def setConfig(gitAutouser, gitHost, gitSshPort, gitProject, repositoryRelativePath) {
        def componentSettings = null
        componentSettings = job.getCodebaseFromAdminConsole(this.name)
        if (componentSettings == null)
            script.error("[JENKINS][ERROR] Component ${this.name} has not been found in configuration")
        componentSettings.cloneUrl = "ssh://${gitAutouser}@${gitHost}:${gitSshPort}${repositoryRelativePath?.trim() ? repositoryRelativePath : "/" + gitProject}"
        this.config = componentSettings
        this.gitServerCrName = componentSettings.gitServer
    }

    def setVCStag(vcsTag) {
        this.vcsTag = vcsTag
    }

    def setIStag(isTag) {
        this.isTag = isTag
    }

    def setVersions(branchVersion, currentBuildNumber, version, buildVersion, isReleaseBranch) {
        this.branchVersion = branchVersion
        this.currentBuildNumber = currentBuildNumber
        this.version = version
        this.buildVersion = buildVersion
        this.isReleaseBranch = isReleaseBranch
    }
}