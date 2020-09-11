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

package com.epam.edp.platform

class Openshift extends Kubernetes {
    Script script

    def getExternalEndpoint(name) {
        return getJsonPathValue("route", name, ".spec.host")
    }

    def getImageStream(imageStreamName, crApiGroup) {
        return script.sh(
                script: "oc get is ${imageStreamName} --ignore-not-found=true --no-headers | awk '{print \$1}'",
                returnStdout: true
        ).trim()
    }

    def getImageStreamTags(imageStreamName, crApiGroup) {
        script.sh(
                script: "oc get is ${imageStreamName} -o jsonpath='{range .spec.tags[*]}{.name}{\"\\n\"}{end}'",
                returnStdout: true
        ).trim().tokenize()
    }

    def protected getTags(imageStreamName, crApiGroup) {
        return script.sh(
                script: "oc get is ${imageStreamName} -o jsonpath='{range .status.tags[*]}{.tag}{\" | \"}{.items[*].created}{\"\\n\"}{end}'",
                returnStdout: true
        ).trim().split('\n')
    }

    def createProjectIfNotExist(name, edpName) {
        script.openshift.withCluster() {
            if (!script.openshift.selector("project", name).exists()) {
                script.openshift.newProject(name)

                def adminGroups = platform.getJsonPathValue("cm", "edp-config", ".data.adminGroups")
                def developerGroups = platform.getJsonPathValue("cm", "edp-config", ".data.developerGroups")

                adminGroups.split(",").each() { group ->
                    script.sh("oc adm policy add-role-to-group admin ${group} -n ${name}")
                }

                developerGroups.split(",").each() { group ->
                    script.sh("oc adm policy add-role-to-group developer ${group} -n ${name}")
                }
            }
        }
    }

    def getObjectList(objectType) {
        script.openshift.withProject() {
            return script.openshift.selector(objectType)
        }
    }

    def copySharedSecrets(sharedSecretName, secretName, project) {
        script.sh("oc get --export -o yaml secret ${sharedSecretName} | " +
                "sed -e 's/name: ${sharedSecretName}/name: ${secretName}/' | " +
                "oc -n ${project} apply -f -")
    }

    def createRoleBinding(user, project) {
        script.sh("oc adm policy add-role-to-user admin ${user} -n ${project}")
    }

    def deployCodebase(project, templateName, codebase, imageName, timeout = null, parametersMap = null, values = null) {
        script.sh("oc -n ${project} process -f ${templateName} " +
                "-p IMAGE_NAME=${imageName} " +
                "-p APP_VERSION=${codebase.version} " +
                "-p NAMESPACE=${project} " +
                "--local=true -o json | oc -n ${project} apply -f -")
    }

    def deployCodebaseHelm(project, chartPath, codebase, imageName = null, timeout = "300s", parametersMap, values = null) {
        def command = "helm upgrade --atomic --force --install ${codebase.name} --wait --timeout=${timeout} --namespace ${project} ${chartPath}"
        if(parametersMap)
            for (param in parametersMap) {
                command = "${command} --set ${param.name}=${param.value}"
            }
        if (values)
            command = "${command} --values ${values}"
        script.sh(command)
    }

    def verifyDeployedCodebase(name, project, kind) {
        script.timeout(600) {
            script.sh("oc -n ${project} rollout status ${kind}/${name}")
        }
    }

    def rollbackDeployedCodebase(name, project, kind) {
        script.sh("oc -n ${project} rollout undo ${kind}/${name}")
    }

    def rollbackDeployedCodebaseHelm(name, project, kind = null) {
        def releaseStatus = script.sh(
                script: "helm -n ${project} status ${name} | grep STATUS | awk '{print \$2}'",
                returnStdout: true
        ).trim()

        if (releaseStatus != "deployed")
            script.sh("helm -n ${project} rollback ${name} --wait --cleanup-on-fail")
        else
            script.println("[JENKINS][DEBUG] Rollback is not needed current status of ${name} is deployed")
    }
}