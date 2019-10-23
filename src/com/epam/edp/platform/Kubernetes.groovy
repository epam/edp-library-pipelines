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
import groovy.json.JsonSlurperClassic

class Kubernetes implements Platform {
    Script script

    def getJsonPathValue(object, name, jsonPath) {
        return script.sh(
                script: "kubectl get ${object} ${name} -o jsonpath='{${jsonPath}}'",
                returnStdout: true
        ).trim()
    }

    def getJsonValue(object, name) {
        return script.sh(
                script: "kubectl get ${object} ${name} -o json",
                returnStdout: true
        ).trim()
    }

    def apply(fileName) {
        script.sh(script: "oc apply -f ${fileName}")
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

    def getObjectStatus(objectType, objectName) {
        def output = getJsonValue(objectType, objectName)
        def parsedInitContainer = new JsonSlurperClassic().parseText(output)
        return parsedInitContainer["status"]
    }

    def getExternalEndpoint(name) {
        return getJsonPathValue("ingress", name, ".spec.rules[0].host")
    }
}