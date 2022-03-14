/* Copyright 2022 EPAM Systems.

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
import groovy.json.JsonSlurper
import com.epam.edp.stages.impl.cd.impl.AutomationTests
import org.apache.maven.artifact.versioning.*

import java.text.DateFormat
import java.text.SimpleDateFormat

class Job {
    final String EDP_EPAM_COM_POSTFIX = "edp.epam.com"

    def type
    Script script
    Platform platform
    def stages = [:]
    def deployTemplatesDirectory
    def deployTimeout
    def edpName
    def stageName
    def deployProject
    def ciProject
    def stageWithoutPrefixName
    def buildUser
    def buildUrl
    def jenkinsUrl
    def codebasesList = []
    def releaseName
    def releaseFromCommitId
    def adminConsoleUrl
    def sharedSecretsMask = "edp-shared-"
    def pipelineName
    def qualityGates = [:]
    def applicationsToPromote = []
    def credentialsId
    def autouser
    def host
    def sshPort
    def maxOfParallelDeployApps
    def crApiVersion = "v2"
    def crApiGroup
    def manualApproveStageTimeout
    def triggerJobName
    def triggerJobWait
    def triggerJobPropogate
    def triggerJobParameters = []
    def codebasePath = ""

    Job(type, platform, script) {
        this.type = type
        this.script = script
        this.platform = platform

        this.script.println("[JENKINS][DEBUG] Created object job with type - ${this.type}")
    }

    def getParameterValue(parameter, defaultValue = null) {
        def parameterValue = script.env["${parameter}"] ? script.env["${parameter}"] : defaultValue
        return parameterValue
    }

    def init() {
        this.crApiVersion = getParameterValue("GIT_SERVER_CR_VERSION")
        this.crApiGroup = "${crApiVersion}.${EDP_EPAM_COM_POSTFIX}"
        this.deployTemplatesDirectory = getParameterValue("DEPLOY_TEMPLATES_DIRECTORY", "deploy-templates")
        this.buildUrl = getParameterValue("BUILD_URL")
        this.jenkinsUrl = getParameterValue("JENKINS_URL")
        this.edpName = platform.getJsonPathValue("cm", "edp-config", ".data.edp_name")
        this.adminConsoleUrl = platform.getJsonPathValue("edpcomponent", "edp-admin-console", ".spec.url")
        this.buildUser = getBuildUser()
        this.triggerJobName = getParameterValue("TRIGGER_JOB_NAME")
        this.triggerJobWait = getParameterValue("TRIGGER_JOB_WAIT", false)
        this.triggerJobPropogate = getParameterValue("TRIGGER_JOB_PROPOGATE", false)
        this.ciProject = getParameterValue("CI_NAMESPACE")
        setTriggerJobParameter()

        def stagesConfig = getParameterValue("STAGES")
        if (!stagesConfig?.trim())
            script.error("[JENKINS][ERROR] Parameter STAGES is mandatory to be specified, please check configuration of job")
        try {
            this.stages = new JsonSlurperClassic().parseText(stagesConfig)
        }
        catch (Exception ex) {
            script.error("[JENKINS][ERROR] Couldn't parse stages configuration from parameter STAGE - not valid JSON formate.\r\nException - ${ex}")
        }

        switch (type) {
            case JobType.CREATERELEASE.value:
                if (!getParameterValue("RELEASE_NAME")) {
                    script.error("[JENKINS][ERROR] Parameter RELEASE_NAME is mandatory to be specified, please check configuration of job")
                }
                if (!getParameterValue("DEFAULT_BRANCH")) {
                    script.error("[JENKINS][ERROR] Parameter DEFAULT_BRANCH is mandatory to be specified, please check configuration of job")
                }
                this.releaseName = getParameterValue("RELEASE_NAME").toLowerCase()
                def defaultBranch = getParameterValue("DEFAULT_BRANCH")
                this.releaseFromCommitId = getParameterValue("COMMIT_ID", "origin/" + defaultBranch)
            case JobType.DEPLOY.value:
                this.maxOfParallelDeployApps = getParameterValue("MAX_PARALLEL_APPS", 5)
        }
    }

    def initDeployJob() {
        this.pipelineName = script.JOB_NAME.split("-cd-pipeline")[0]
        this.stageName = script.JOB_NAME.split('/')[1]
        def codebaseBranchList = [:]
        def tmpAccessToken = getTokenFromAdminConsole()
        def stageContent = getStageFromAdminConsole(this.pipelineName, stageName, "cd-pipeline", tmpAccessToken)
        def pipelineContent = getPipelineFromAdminConsole(this.pipelineName, "cd-pipeline", tmpAccessToken)
        this.codebasesList = getCodebaseFromAdminConsole(pipelineContent.codebaseBranches.appName, tmpAccessToken)
        this.applicationsToPromote = pipelineContent.applicationsToPromote
        this.qualityGates = stageContent.qualityGates
        this.stageWithoutPrefixName = "${this.pipelineName}-${stageName}"
        this.deployProject = "${this.edpName}-${this.pipelineName}-${stageName}"
        this.ciProject = getParameterValue("CI_NAMESPACE")
        this.deployTimeout = getParameterValue("DEPLOY_TIMEOUT", "300s")
        this.manualApproveStageTimeout = getParameterValue("MANUAL_APPROVE_TIMEOUT", "10")

        stageContent.applications.each() { item ->
            codebaseBranchList["${item.name}"] = ["inputIs" : item.inputIs,
                                                  "outputIs": item.outputIs]
        }

        codebasesList.each() { codebase ->
            codebase.inputIs  = codebaseBranchList["${codebase.name}"].inputIs.replaceAll("[^\\p{L}\\p{Nd}]+", "-")
            codebase.outputIs = codebaseBranchList["${codebase.name}"].outputIs.replaceAll("[^\\p{L}\\p{Nd}]+", "-")
            setCodebaseTags(codebase)
        }
    }

    def setCodebaseTags(codebase) {
        def cbisSet = "${codebase.inputIs} ${codebase.outputIs}"
        def cbisData = script.sh(
                script: "kubectl get codebaseimagestreams.v2.edp.epam.com ${cbisSet} --ignore-not-found=true --output=json",
                returnStdout: true
        ).trim()
        def cbisJsonData = new JsonSlurperClassic().parseText(cbisData)
        def images = null
        if (cbisJsonData.items.spec.tags.name[0] != null){
            images = cbisJsonData.items.spec.tags.name[0]
        }
        def tags = ['noImageExists']
        if (images != null) {
            tags = images.reverse()
        }
        def latestTag = getLatestTag(tags.collect{ (it=~/\d+|\D+/).findAll() }.sort().reverse().collect{ it.join() })
        codebase.latest = latestTag
        if (tags != ['noImageExists']) {
            tags.add(0, "No deploy")
        }
        tags = setLatestLabelOnTag(tags, latestTag)
        if (cbisJsonData.items.size() > 1) {
            def stableTag = getStableTag(cbisJsonData.items[1].spec.tags)
            codebase.stable = stableTag
            tags = setStableLabelOnTag(tags, stableTag)
        }
        codebase.sortedTags = tags
        script.println("[JENKINS][DEBUG] Existing tags for ${codebase.name}: ${tags}")
    }

    def getLatestTag(tags){
        if (tags == ['noImageExists']){
            return null
        }
        return tags[0]
    }

    def setLatestLabelOnTag(tags, latestTag) {
        if (latestTag == null){
            return tags
        }
        tags.add(1, "latest (${latestTag})")
        script.println("[JENKINS][DEBUG] Latest tag: ${latestTag}")
        return tags
    }

    def getStableTag(verifiedTags){
        if (verifiedTags == null){
            return null
        }
        def codebasesVerifiedTagsList = verifiedTags.name
        def codebasesStableTag = codebasesVerifiedTagsList[codebasesVerifiedTagsList.size()-1]
        return codebasesStableTag
    }

    def setStableLabelOnTag(tags, stableTag) {
        if (stableTag == null){
            return tags
        }
        tags.add(2, "stable (${stableTag})")
        script.println("[JENKINS][DEBUG] Stable tag: ${stableTag}")
        return tags
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
        if (addDescription && script.currentBuild.description?.trim())
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

    def runStage(stageName, context, runStageName = null) {
        script.stage(runStageName ? runStageName : stageName) {
            if (context.codebase) {
                context.factory.getStage(stageName.toLowerCase(),
                        context.codebase.config.build_tool.toLowerCase(),
                        context.codebase.config.type).run(context)
            } else {
                run(stageName, context, runStageName)
            }
        }
    }

    def run(stageName, context, runStageName = null) {
        def stage = context.factory.getStage(stageName.toLowerCase())
        if (stage.getClass() == AutomationTests) {
            stage.run(context, runStageName)
            return
        }
        stage.run(context)
    }

    def failStage(stageName, exception) {
        script.println "[JENKINS][ERROR] Trace: ${exception.getStackTrace().collect { it.toString() }.join('\n')}"
        script.updateGitlabCommitStatus name: 'Jenkins', state: "failed"
        script.error("[JENKINS][ERROR] Stage ${stageName} has failed\r\n Exception - ${exception}")
    }

    private def getBuildCause() {
        return platform.getJsonPathValue("build", "${this.deployProject}-deploy-pipeline-${script.BUILD_NUMBER}", ".spec.triggeredBy[0].message")
    }

    def getTokenFromAdminConsole() {
        def clientSecret = getSecretField("admin-console-client", "clientSecret")
        def clientUsername = getSecretField("admin-console-client", "username")
        def basicAuth = "${clientUsername}:${clientSecret}".bytes.encodeBase64().toString()
        def keycloakUrl = platform.getJsonPathValue("edpcomponent", "main-keycloak", ".spec.url")
        def realmName = platform.getJsonPathValue("keycloakrealm", "main", ".spec.realmName")

        def response = script.httpRequest url: "${keycloakUrl}/realms/${realmName}/protocol/openid-connect/token",
                httpMode: 'POST',
                contentType: 'APPLICATION_FORM',
                requestBody: "grant_type=client_credentials",
                customHeaders: [[name: 'Authorization', value: "Basic ${basicAuth}"]],
                consoleLogResponseBody: false

        return new JsonSlurperClassic()
                .parseText(response.content)
                .access_token
    }

    def getCodebaseFromAdminConsole(codebaseNames = null, tmpToken = null) {
        def accessToken = tmpToken ?: getTokenFromAdminConsole()
        def url = getCodebaseRequestUrl(codebaseNames)
        def response = script.httpRequest url: "${url}",
                httpMode: 'GET',
                customHeaders: [[name: 'Authorization', value: "Bearer ${accessToken}"]],
                consoleLogResponseBody: false
        return new JsonSlurperClassic().parseText(response.content)
    }

    def getCodebaseRequestUrl(codebaseName = null) {
        if (codebaseName.getClass() == java.lang.String) {
            return "${adminConsoleUrl}/api/v2/edp/codebase/${codebaseName}"
        }
        if (codebaseName.getClass() == java.util.ArrayList) {
            def codebases = codebaseName.join(",")
            return "${adminConsoleUrl}/api/v2/edp/codebase?codebases=${codebases}"
        }
        return "${adminConsoleUrl}/api/v1/edp/codebase"
    }

    def getStageFromAdminConsole(pipelineName, stageName, pipelineType, tmpToken = null) {
        def accessToken = tmpToken ?: getTokenFromAdminConsole()

        def url = "${adminConsoleUrl}" + "/api/v2/edp/${pipelineType}/${pipelineName}/stage/${stageName}"
        def response = script.httpRequest url: "${url}",
                httpMode: 'GET',
                customHeaders: [[name: 'Authorization', value: "Bearer ${accessToken}"]],
                consoleLogResponseBody: false

        return new JsonSlurperClassic().parseText(response.content)
    }

    def getPipelineFromAdminConsole(pipelineName, pipelineType, tmpToken = null) {
        def accessToken = tmpToken ?: getTokenFromAdminConsole()

        def url = "${adminConsoleUrl}" + "/api/v2/edp/${pipelineType}/${pipelineName}"
        def response = script.httpRequest url: "${url}",
                httpMode: 'GET',
                customHeaders: [[name: 'Authorization', value: "Bearer ${accessToken}"]],
                consoleLogResponseBody: false

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

    private def setTriggerJobParameter() {
        def triggerJobParameterEnvValue = getParameterValue("TRIGGER_JOB_PARAMETERS")
        if (!triggerJobParameterEnvValue)
            return

        def parsedTriggerJobParameter = new JsonSlurperClassic().parseText(triggerJobParameterEnvValue)
        for (param in parsedTriggerJobParameter) {
            this.triggerJobParameters.push(script.string(name: param.name, value: param.value))
        }
    }
}
