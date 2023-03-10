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
import groovy.json.*

class Nexus {
    Script script
    Job job
    Platform platform

    def autouser
    def credentialsId
    def host
    def port
    def basePath
    def repositoriesUrl
    def restUrl
    def baseUrl

    Nexus(job, platform, script) {
        this.script = script
        this.job = job
        this.platform = platform
    }

    def init() {
        this.autouser = job.getParameterValue("NEXUS_AUTOUSER", "jenkins")
        this.credentialsId = job.getParameterValue("NEXUS_CREDENTIALS", "nexus-ci.user")
        if (platform.checkObjectExists("nexus","nexus"))
        {
            script.println("[JENKINS][DEBUG] Nexus CR exists")
            this.host = job.getParameterValue("NEXUS_HOST", "nexus")
            this.port = job.getParameterValue("NEXUS_HTTP_PORT", "8081")
            basePath = platform.getJsonPathValue("nexus", "nexus", ".spec.basePath")
            this.basePath = basePath != "" ? "${basePath}" : ""
            this.baseUrl = "http://${this.host}:${this.port}${this.basePath}"
        }
        else
        {
            script.println("[JENKINS][DEBUG] Nexus CR does not exist")
            this.baseUrl = platform.getJsonPathValue("edpcomponent", "nexus", ".spec.url")
        }
        this.repositoriesUrl = "${this.baseUrl}/repository"
        this.restUrl = "${this.baseUrl}/service/rest"
    }



    def uploadGroovyScriptToNexus(scriptName, pathToScript) {
        def requestBody = [:]
        requestBody['content'] = script.readFile pathToScript
        requestBody['name'] = scriptName
        requestBody['type'] = "groovy"
        requestBody = JsonOutput.toJson(requestBody)

        //Check if script exists
        def response = getNexusGroovyScript(scriptName)
        if (response.status == 404)
            addNexusGroovyScript(requestBody)
        else if (response.status == 200)
            println("Script ${scriptName} is already uploaded")
    }

    def deleteNexusGroovyScript(name) {
        script.httpRequest authentication: "${this.credentialsId}",
                httpMode: 'DELETE',
                url: "${this.restUrl}/v1/script/${name}",
                contentType: 'APPLICATION_JSON',
                validResponseCodes: '204,404'
    }

    def addNexusGroovyScript(requestBody) {
        script.httpRequest authentication: "${this.credentialsId}",
                httpMode: 'POST',
                url: "${this.restUrl}/v1/script",
                contentType: 'APPLICATION_JSON',
                requestBody: requestBody
    }

    def runNexusGroovyScript(name, requestBody) {
        script.httpRequest authentication: "${this.credentialsId}",
                httpMode: 'POST',
                url: "${this.restUrl}/v1/script/${name}/run",
                contentType: 'TEXT_PLAIN',
                requestBody: requestBody
    }

    def getNexusGroovyScript(name) {
        script.httpRequest authentication: "${this.credentialsId}",
                httpMode: 'GET',
                url: "${this.restUrl}/v1/script/${name}",
                validResponseCodes: '200,404'
    }

}
