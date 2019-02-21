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

class Gerrit {
    Script script
    Platform platform
    Job job

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

    Gerrit(job, platform, script) {
        this.script = script
        this.platform = platform
        this.job = job
    }

    def init() {
        this.credentialsId = job.getParameterValue("GERRIT_CREDENTIALS", "jenkins")
        this.autouser = job.getParameterValue("GERRIT_AUTOUSER", "jenkins")
        this.host = job.getParameterValue("GERRIT_HOST", "gerrit")
        this.project = job.getParameterValue("GERRIT_PROJECT")
        this.branch = job.getParameterValue("GERRIT_BRANCH")
        if (!this.branch)
            this.branch = job.getParameterValue("BRANCH", "master")
        this.patchsetNumber = job.getParameterValue("GERRIT_PATCHSET_NUMBER")
        this.changeNumber = job.getParameterValue("GERRIT_CHANGE_NUMBER")
        this.changeName = "change-${this.changeNumber}-${this.patchsetNumber}"
        this.refspecName = job.getParameterValue("GERRIT_REFSPEC")
        if (this.project == null)
            this.project = job.getParameterValue("GERRIT_PROJECT_NAME")
        if (this.project == null)
            script.error("[JENKINS][ERROR] Couldn't determine project, please make sure that GERRIT_PROJECT_NAME variable is defined")
        this.sshPort = platform.getJsonPathValue("svc","gerrit",".spec.ports[?(@.name==\"ssh\")].targetPort")
    }
}