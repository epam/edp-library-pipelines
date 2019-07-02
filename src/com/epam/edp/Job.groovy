/* Copyright 2019 EPAM Systems.

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

class Job {
    def type
    Script script
    Platform platform
    def stages = [:]
    def deployTemplatesDirectory
    def edpName
    def stageName
    def metaProject
    def deployProject
    def stageWithoutPrefixName
    def buildCause
    def buildUser
    def buildUrl
    def jenkinsUrl
    def codebasesList = []
    def servicesList = []
    def stageAutotestsList = []
    def userInputImagesToDeploy
    def inputProjectPrefix
    def promotion = [:]
    def releaseName
    def releaseFromCommitId
    def adminConsoleUrl
    def sharedSecretsMask = "edp-shared-"
    def pipelineName
    def qualityGate
    def qualityGateName
    def autotestName
    def testReportFramework
    def autotestBranch

    Job(type, platform, script) {
        this.type = type
        this.script = script
        this.platform = platform
    }

    def getParameterValue(parameter, defaultValue = null) {
        def parameterValue = script.env["${parameter}"] ? script.env["${parameter}"] : defaultValue
        return parameterValue
    }

    def init() {
        this.deployTemplatesDirectory = getParameterValue("DEPLOY_TEMPLATES_DIRECTORY", "deploy-templates")
        this.buildUrl = getParameterValue("BUILD_URL")
        this.jenkinsUrl = getParameterValue("JENKINS_URL")
        this.edpName = platform.getJsonPathValue("cm", "user-settings", ".data.edp_name")
        this.adminConsoleUrl = platform.getJsonPathValue("cm", "user-settings", ".data.admin_console_url")
        this.codebasesList = getCodebaseFromAdminConsole()
        this.servicesList = getProjectConfiguration("service.settings.json")
        this.buildUser = getBuildUser()
        switch (type) {
            case JobType.CREATERELEASE.value:
                this.releaseName = getParameterValue("RELEASE_NAME").toLowerCase()
                if (!this.releaseName) {
                    script.error("[JENKINS][ERROR] Parameter RELEASE_NAME is mandatory to be specified, please check configuration of job")
                }
                this.releaseFromCommitId = getParameterValue("COMMIT_ID", "")
            case [JobType.BUILD.value, JobType.CODEREVIEW.value, JobType.CREATERELEASE.value]:
                def stagesConfig = getParameterValue("STAGES")
                if (!stagesConfig?.trim())
                    script.error("[JENKINS][ERROR] Parameter STAGES is mandatory to be specified, please check configuration of job")
                try {
                    this.stages = new JsonSlurperClassic().parseText(stagesConfig)
                }
                catch (Exception ex) {
                    script.error("[JENKINS][ERROR] Couldn't parse stages configuration from parameter STAGE - not valid JSON formate.\r\nException - ${ex}")
                }
        }
    }

    def initDeployJob() {
        this.pipelineName = script.JOB_NAME.split("-cd-pipeline")[0]
        this.stageName = script.JOB_NAME.split('/')[1]
        this.metaProject = "${this.edpName}-edp-cicd"
        def stageCodebasesList = []
        def codebaseBranchList = [:]
        def stageContent = getStageFromAdminConsole(this.pipelineName, stageName, "cd-pipeline")
        def pipelineContent = getPipelineFromAdminConsole(this.pipelineName, "cd-pipeline")
        this.servicesList = pipelineContent.services
        this.qualityGate = stageContent.qualityGate
        this.qualityGateName = stageContent.jenkinsStepName
        this.stageWithoutPrefixName = "${this.pipelineName}-${stageName}"
        this.deployProject = "${this.edpName}-${this.pipelineName}-${stageName}"
        stageContent.applications.each() { item ->
            stageCodebasesList.add(item.name)
            codebaseBranchList["${item.name}"] = ["branch"   : item.branchName,
                                                  "inputIs" : item.inputIs,
                                                  "outputIs": item.outputIs]
        }

        stageContent.autotests.each() { item ->
            stageAutotestsList.add(item)
        }

        def iterator = codebasesList.listIterator()
        while (iterator.hasNext()) {
            if (!stageCodebasesList.contains(iterator.next().name)) {
                iterator.remove()
            }
        }

        codebasesList.each() { item ->
            item.branch = codebaseBranchList["${item.name}"].branch
            item.normalizedName = "${item.name}-${item.branch.replaceAll("[^\\p{L}\\p{Nd}]+", "-")}"
            item.inputIs = codebaseBranchList["${item.name}"].inputIs.replaceAll("[^\\p{L}\\p{Nd}]+", "-")
            item.outputIs = codebaseBranchList["${item.name}"].outputIs.replaceAll("[^\\p{L}\\p{Nd}]+", "-")

        }
    }

    def getBuildUser() {
        script.wrap([$class: 'BuildUser']) {
            def userId = getParameterValue("BUILD_USER_ID")
            return userId
        }
    }

    def setBuildResult(result) {
        script.currentBuild.result = result
    }

    def setDisplayName(displayName) {
        script.currentBuild.displayName = displayName
    }

    def setDescription(description, addDescription = false) {
        if (addDescription)
            script.currentBuild.description = "${script.currentBuild.description}\r\n${description}"
        else
            script.currentBuild.description = description
    }

    void printDebugInfo(context) {
        def debugOutput = ""
        context.keySet().each { key ->
            debugOutput = debugOutput + "${key}=${context["${key}"]}\n"
        }
        script.println("[JENKINS][DEBUG] Pipeline's context:\n${debugOutput}")
    }

    def runStage(stageName, context) {
        script.stage(stageName) {
            if (context.codebase)
                context.factory.getStage(stageName.toLowerCase(),
                        context.codebase.config.build_tool.toLowerCase(),
                        context.codebase.config.type).run(context)
            else
                context.factory.getStage(stageName.toLowerCase()).run(context)
        }
    }

    private def getBuildCause() {
        return platform.getJsonPathValue("build", "${this.deployProject}-deploy-pipeline-${script.BUILD_NUMBER}", ".spec.triggeredBy[0].message")
    }

    private def getProjectConfiguration(configMapKey) {
        def json = platform.getJsonPathValue("cm", "project-settings", ".data.${configMapKey.replaceAll("\\.", "\\\\.")}")
        return new JsonSlurperClassic().parseText(json)
    }

    def getTokenFromAdminConsole() {
        def userCredentials = getCredentialsFromSecret("admin-console-reader")
        def clientCredentials = getCredentialsFromSecret("admin-console-client")

        def dnsWildcard = platform.getJsonPathValue("cm", "user-settings", ".data.dns_wildcard")

        def response = script.httpRequest url: "https://keycloak-security.${dnsWildcard}/auth/realms/${this.edpName}-edp/protocol/openid-connect/token",
                httpMode: 'POST',
                contentType: 'APPLICATION_FORM',
                requestBody: "grant_type=password&username=${userCredentials.username}&password=${userCredentials.password}" +
                        "&client_id=${clientCredentials.username}&client_secret=${clientCredentials.password}",
                consoleLogResponseBody: true

        return new JsonSlurperClassic()
                .parseText(response.content)
                .access_token
    }

    def getCodebaseFromAdminConsole(codebaseName = null) {
        def accessToken = getTokenFromAdminConsole()

        def url = "${adminConsoleUrl}/api/v1/edp/codebase${codebaseName ? "/${codebaseName}" : ""}"
        def response = script.httpRequest url: "${url}",
                httpMode: 'GET',
                customHeaders: [[name: 'Authorization', value: "Bearer ${accessToken}"]],
                consoleLogResponseBody: true

        return new JsonSlurperClassic().parseText(response.content.toLowerCase())
    }

    def getStageFromAdminConsole(pipelineName, stageName, pipelineType) {
        def accessToken = getTokenFromAdminConsole()

        def url = "${adminConsoleUrl}" + "/api/v1/edp/${pipelineType}/${pipelineName}/stage/${stageName}"
        def response = script.httpRequest url: "${url}",
                httpMode: 'GET',
                customHeaders: [[name: 'Authorization', value: "Bearer ${accessToken}"]],
                consoleLogResponseBody: true

        return new JsonSlurperClassic().parseText(response.content)
    }

    def getPipelineFromAdminConsole(pipelineName, pipelineType) {
        def accessToken = getTokenFromAdminConsole()

        def url = "${adminConsoleUrl}" + "/api/v1/edp/${pipelineType}/${pipelineName}"
        def response = script.httpRequest url: "${url}",
                httpMode: 'GET',
                customHeaders: [[name: 'Authorization', value: "Bearer ${accessToken}"]],
                consoleLogResponseBody: true

        return new JsonSlurperClassic().parseText(response.content)
    }

    private def getCredentialsFromSecret(name) {
        def credentials = [:]
        credentials['username'] = getSecretField(name, 'username')
        credentials['password'] = getSecretField(name, 'password')
        return credentials
    }

    private def getSecretField(name, field) {
        return new String(platform.getJsonPathValue("secret", name, ".data.\\\\${field}").decodeBase64())
    }


}