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

import groovy.json.JsonSlurperClassic
import org.apache.commons.lang.RandomStringUtils

def getJsonPathValue(object, name, jsonPath) {
    sh(
            script: "oc get ${object} ${name} -o jsonpath='{${jsonPath}}'",
            returnStdout: true
    ).trim()
}

def getCodebaseFromAdminConsole(codebaseName = null) {
    def accessToken = getTokenFromAdminConsole()
    def adminConsoleUrl = getJsonPathValue("edpcomponent", "edp-admin-console", ".spec.url")
    def url = "${adminConsoleUrl}/api/v1/edp/codebase${codebaseName ? "/${codebaseName}" : ""}"
    def response = httpRequest url: "${url}",
            httpMode: 'GET',
            customHeaders: [[name: 'Authorization', value: "Bearer ${accessToken}"]],
            consoleLogResponseBody: false

    return new groovy.json.JsonSlurperClassic().parseText(response.content.toLowerCase())
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

private def getCredentialsFromSecret(name) {
    def credentials = [:]
    credentials['username'] = getSecretField(name, 'username')
    credentials['password'] = getSecretField(name, 'password')
    return credentials
}

private def getSecretField(name, field) {
    return new String(getJsonPathValue("secret", name, ".data.\\\\${field}").decodeBase64())
}

node("master") {
    if (!env.DEV_GIT_SERVER || !env.DEV_GIT_PORT) {
        error("[JENKINS][ERROR] DEV_GIT_SERVER and DEV_GIT_PORT environment variables are mandatory to be set")
    }
    def workDir = new File("/tmp/${RandomStringUtils.random(10, true, true)}")
    def devGitUrl = "ssh://jenkins@${DEV_GIT_SERVER}:${DEV_GIT_PORT}/"

    openshift.withCluster {
        openshift.withProject() {
            prodGitHost = "gerrit.${openshift.project()}"
        }
    }

    def prodGitPort = getJsonPathValue("service", "gerrit", ".spec.ports[?(@.name==\"ssh\")].nodePort")
    def prodGitUrl = "ssh://jenkins@${prodGitHost}:${prodGitPort}/"

    stage("Copy repositories") {
        def appList = getCodebaseFromAdminConsole()
        sh """
                 grep -q ${DEV_GIT_SERVER} ~/.ssh/known_hosts || ssh-keyscan -p ${DEV_GIT_PORT} ${DEV_GIT_SERVER} >> ~/.ssh/known_hosts
                 grep -q ${prodGitHost} ~/.ssh/known_hosts || ssh-keyscan -p ${prodGitPort} ${prodGitHost} >> ~/.ssh/known_hosts
          """
        appList.each() { app ->
            dir("${workDir}/${app.name}") {
                println("[JENKINS][DEBUG] Transferring ${app.name}")
                try {
                    sh """
                 git clone --mirror ${devGitUrl}${app.name}
                 cd ${app.name}.git
                 git fetch --tags
                 git push --all ${prodGitUrl}${app.name}
                 git push --tags ${prodGitUrl}${app.name}
                 cd ..
                 rm -rf ${app.name}.git
                """
                }
                catch(Exception ex) {
                    unstable("[JENKINS][DEBUG] Failed to transfer ${app.name} with error ${ex}")
                }
            }
        }
    }
    sh "rm -rf ${workDir}"
}
