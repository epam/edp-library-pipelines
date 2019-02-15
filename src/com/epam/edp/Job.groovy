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

class Job {
    def type
    Script script
    Platform platform
    def stages = [:]
    def environmentsConfig = [:]
    def deployTemplatesDirectory
    def edpName
    def envToPromote = null
    boolean promoteImages = true


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
        this.edpName = platform.getJsonPathValue("cm","user-settings",".data.edp_name")
        def stagesConfig = getParameterValue("STAGES")
        if (!stagesConfig?.trim())
            script.error("[JENKINS][ERROR] Parameter STAGES is mandatory to be specified? please check configuration of job")
        try {
            this.stages = new JsonSlurperClassic().parseText(stagesConfig)
        }
        catch (Exception ex) {
            script.error("[JENKINS][ERROR] Couldn't parse stages configuration from parameter STAGE - not valid JSON formate.\r\nException - ${ex}")
        }
        if (type == JobType.BUILD.value) {
            def environmentsData = platform.getJsonPathValue("cm", "project-settings", ".data.env\\.settings\\.json")
            this.environmentsConfig = new JsonSlurperClassic().parseText(environmentsData)
            if (environmentsConfig)
                this.envToPromote = "${environmentsConfig[0].name}-meta"
            else {
                println("[JENKINS][WARNING] There are no environments were added to the project, we won't promote image after build config\r\n" +
                        "[JENKINS][WARNING] If your like to promote your images please add environment via your cockpit panel")
                this.promoteImages = false
            }
        }

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
            context.factory.getStage(stageName.toLowerCase(),
                    context.application.config.build_tool.toLowerCase(),
                    context.application.config.type).run(context)
        }
    }
}