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

def getJsonPathValue(object, name, jsonPath) {
    sh(
            script: "oc get ${object} ${name} -o jsonpath='{${jsonPath}}'",
            returnStdout: true
    ).trim()
}

def getGoTemplateValue(object, goTemplates) {
    sh(
            script: "oc get ${object} -o go-template='${goTemplates}'",
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
    def userCredentials = getCredentialsFromSecret("ac-reader")
    def clientCredentials = getCredentialsFromSecret("admin-console-client")
    def keycloakUrl = getJsonPathValue("edpcomponent", "main-keycloak", ".spec.url")

    def realmName = getJsonPathValue("keycloakrealm.v1.edp.epam.com", "main", ".spec.realmName")
    def response = httpRequest url: "${keycloakUrl}/realms/${realmName}/protocol/openid-connect/token",
            httpMode: 'POST',
            contentType: 'APPLICATION_FORM',
            requestBody: "grant_type=password&username=${userCredentials.username}&password=${userCredentials.password}" +
                    "&client_id=${clientCredentials.username}&client_secret=${clientCredentials.password}",
            consoleLogResponseBody: true

    return new JsonSlurperClassic()
            .parseText(response.content)
            .access_token
}

def getStageFromAdminConsole(pipelineName, stageName, pipelineType) {
    def accessToken = getTokenFromAdminConsole()
    def adminConsoleUrl = getJsonPathValue("edpcomponent", "edp-admin-console", ".spec.url")
    def url = "${adminConsoleUrl}" + "/api/v1/edp/${pipelineType}/${pipelineName}/stage/${stageName}"
    def response = httpRequest url: "${url}",
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
    return new String(getJsonPathValue("secret", name, ".data.\\\\${field}").decodeBase64())
}

node("master") {
    if (!env.DEV_REGISTRY_URL) {
        error("[JENKINS][ERROR] DEV_REGISTRY_URL environment variables is mandatory to be set")
    }

    openshift.withCluster {
        openshift.withProject() {
            projectName = openshift.project()
        }
    }

    stage("Images sync") {
        def appList = getCodebaseFromAdminConsole()

        appList.each() { app ->
            def branches = getGoTemplateValue("codebasebranches.v2.edp.epam.com",
                    "{{range .items}}{{if eq .spec.codebaseName \"${app.name}\"}}{{.spec.branchName}}{{\" \"}}{{end}}{{end}}").
                    replaceAll("\\.", '-').split(' ')
            branches.each() { branch ->
                println("[JENKINS][DEBUG] Transferring ${app.name}-${branch} repository")
                try {
                    sh "oc -n ${projectName} import-image ${app.name}-${branch} " +
                            "--from=${DEV_REGISTRY_URL}/${app.name}-${branch} --confirm=true --reference-policy=local --all --insecure=true"
                    sh "oc -n ${projectName} set image-lookup ${app.name}-${branch}"
                }
                catch(Exception ex) {
                    unstable("[JENKINS][DEBUG] Failed to transfer ${app.name}-${branch} repository with error ${ex}")
                }
            }
        }

        def stages = getGoTemplateValue("stages.v2.edp.epam.com",
                "{{range .items}}{{.metadata.name}}{{\" \"}}{{end}}").split(' ')
        stages.each() { stage ->
            def pipelineName = getJsonPathValue("stages.v2.edp.epam.com", stage, ".spec.cdPipeline")
            def stageName = getJsonPathValue("stages.v2.edp.epam.com", stage, ".spec.name")

            def stageContent = getStageFromAdminConsole(pipelineName, stageName, "cd-pipeline")

            stageContent.applications.each() { app ->
                println("[JENKINS][DEBUG] Transferring ${app.outputIs} repository")
                try {
                    sh "oc -n ${projectName} import-image ${app.outputIs} " +
                            "--from=${DEV_REGISTRY_URL}/${app.outputIs} --confirm=true --reference-policy=local --all --insecure=true"
                    sh "oc -n ${projectName} set image-lookup ${app.outputIs}"
                }
                catch(Exception ex) {
                    unstable("[JENKINS][DEBUG] Failed to transfer ${app.outputIs} repository with error ${ex}")
                }
            }
        }
    }
}
