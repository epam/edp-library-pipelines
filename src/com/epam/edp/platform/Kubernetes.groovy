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

package com.epam.edp.platform
import groovy.json.JsonSlurperClassic

class Kubernetes implements Platform {
    Script script

    def getJsonPathValue(object, name, jsonPath, project = null) {
        def command = "kubectl get ${object} ${name} -o jsonpath='{${jsonPath}}'"
        if (project)
            command = "${command} -n ${project}"
        return script.sh(
                script: command,
                returnStdout: true
        ).trim()
    }

    def getJsonValue(object, name, project = null) {
        def command = "kubectl get ${object} ${name} -o json"
        if (project)
            command = "${command} -n ${project}"
        return script.sh(
                script: command,
                returnStdout: true
        ).trim()
    }

    def getImageStream(imageStreamName, crApiGroup) {
        return script.sh(
                script: "kubectl get cbis.${crApiGroup} ${imageStreamName} --ignore-not-found=true --no-headers -o=custom-columns=NAME:.metadata.name",
                returnStdout: true
        ).trim()
    }

    def getImageStreamTags(imageStreamName, crApiGroup) {
        script.sh(
                script: "kubectl get cbis.${crApiGroup} ${imageStreamName} -o jsonpath='{range .spec.tags[*]}{.name}{\"\\n\"}{end}'",
                returnStdout: true
        ).trim().tokenize()
    }

    def getImageStreamTagsWithTime(imageStreamName, crApiGroup) {
        def tags = getTags(imageStreamName, crApiGroup)
        if (tags == null || tags.size() == 0) {
            return null
        }

        return tags.collectEntries {
            def s = it.split(" | ")
            "latest" != s[0] ? [(s[0]): s[2]] : [:]
        }
    }

    def protected getTags(imageStreamName, crApiGroup) {
        def tags = script.sh(
                script: "kubectl get cbis.${crApiGroup} ${imageStreamName} -o jsonpath='{range .spec.tags[*]}{.name}{\" | \"}{.created}{\"\\n\"}{end}'",
                returnStdout: true
        ).trim()
        if (tags.size() == 0) {
            return null
        }

        return tags.split('\n')
    }

    def apply(fileName) {
        script.sh(script: "kubectl apply -f ${fileName}")
    }

    def deleteObject(objectType, objectName, force = false) {
        def command = "kubectl delete ${objectType} ${objectName}"
        if (force) {
            command = "${command} --force --grace-period=0"
        }
        try {
            script.sh(script: "${command}")
        } catch(Exception ex){}
    }

    def copyToPod(source, destination, podName,podNamespace = null, podContainerName = null) {
        def command = "kubectl cp ${source} "

        if (podNamespace)
            command = "${command}${podNamespace}/"

        command = "${command}${podName}:${destination}"

        if (podContainerName)
            command = "${command} -c ${podContainerName}"
        script.sh(script: "${command}")
    }

    def getObjectStatus(objectType, objectName, project = null) {
        def output = getJsonValue(objectType, objectName, project)
        def parsedInitContainer = new JsonSlurperClassic().parseText(output)
        return parsedInitContainer["status"]
    }

    def checkObjectExists(objectType, objectName, project = null) {
        def command = "kubectl get ${objectType} ${objectName} --ignore-not-found=true"
        if (project)
            command = "${command} -n ${project}"

        def res = script.sh(
                script: command,
                returnStdout: true
        ).trim()
        if (res == "")
            return false
        return true
    }

    def getObjectList(objectType) {
        return script.sh(
                script: "kubectl get ${objectType} -o jsonpath='{.items[*].metadata.name}'",
                returnStdout: true
        ).trim().tokenize()
    }

    def createRoleBinding(user, role, project) {
        println("[JENKINS][DEBUG] Security model for kubernetes hasn't been defined yet")
    }

    def addSccToUser(user,scc, project) {
        println("[JENKINS][DEBUG] Security model for kubernetes hasn't been defined yet")
    }

    def deployCodebaseHelm(project, chartPath, codebase, imageName = null, timeout = "300s", parametersMap, values = null) {
        def helmDependencyUpdateCommand = "helm dependency update ${chartPath}"
        def command = "helm upgrade --atomic --install ${codebase.name} --wait --timeout=${timeout} --namespace ${project} ${chartPath}"
        if(parametersMap)
            for (param in parametersMap) {
                command = "${command} --set ${param.name}=${param.value}"
            }
        if (values)
            command = "${command} --values ${values}"
        script.sh(helmDependencyUpdateCommand)
        script.sh(command)
    }

    def deployCodebase(project, templateName, codebase, imageName, timeout = null, parametersMap = null, values = null) {
        println("[JENKINS][DEBUG] Use deployCodebaseHelm for Kubernetes")
    }

    def createFullImageName(registryHost,ciProject,imageName) {
        return "${registryHost}/${ciProject}/${imageName}"
    }
}
