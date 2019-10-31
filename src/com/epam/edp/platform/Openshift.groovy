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
    def promoteStageName = "Promote-images"

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

    def createProjectIfNotExist(name, edpName) {
        script.openshift.withCluster() {
            if (!script.openshift.selector("project", name).exists()) {
                script.openshift.newProject(name)
                def groupList = ["${edpName}-edp-super-admin", "${edpName}-edp-admin"]
                groupList.each() { group ->
                    script.sh("oc adm policy add-role-to-group admin ${group} -n ${name}")
                }
                script.sh("oc adm policy add-role-to-group view ${edpName}-edp-view -n ${name}")
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

    def deployCodebase(project, templateName, imageName, codebase, dnsWildcard = null, timeout = null, isDeployed = null) {
        script.sh("oc -n ${project} process -f ${templateName} " +
                "-p IMAGE_NAME=${imageName} " +
                "-p APP_VERSION=${codebase.version} " +
                "-p NAMESPACE=${project} " +
                "--local=true -o json | oc -n ${project} apply -f -")
    }

    def verifyDeployedCodebase(name, project) {
        script.openshiftVerifyDeployment apiURL: '', authToken: '', depCfg: "${name}",
                namespace: "${project}", verbose: 'false',
                verifyReplicaCount: 'true', waitTime: '600', waitUnit: 'sec'
    }

    def rollbackDeployedCodebase(name, project) {
        script.sh("oc -n ${project} rollout undo dc ${name}")
    }
}