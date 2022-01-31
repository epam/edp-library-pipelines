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

class Openshift extends Kubernetes {
    Script script

    def getImageStream(imageStreamName, crApiGroup) {
        return script.sh(
                script: "oc get cbis.${crApiGroup} ${imageStreamName} --ignore-not-found=true --no-headers -o=custom-columns=NAME:.metadata.name",
                returnStdout: true
        ).trim()
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

    def getImageStreamTags(imageStreamName, crApiGroup) {
        script.sh(
                script: "oc get cbis.${crApiGroup} ${imageStreamName} -o jsonpath='{range .spec.tags[*]}{.name}{\"\\n\"}{end}'",
                returnStdout: true
        ).trim().tokenize()
    }

    def protected getTags(imageStreamName, crApiGroup) {
        def tags = script.sh(
                script: "oc get cbis.${crApiGroup} ${imageStreamName} -o jsonpath='{range .spec.tags[*]}{.name}{\" | \"}{.created}{\"\\n\"}{end}'",
                returnStdout: true
        ).trim()
        if (tags.size() == 0) {
            return null
        }

        return tags.split('\n')
    }

    def getObjectList(objectType) {
        script.openshift.withCluster() {
            script.openshift.withProject() {
                return script.openshift.selector(objectType)
            }
        }
    }

    def createRoleBinding(user, role, project) {
        script.sh("oc adm policy add-role-to-user ${role} ${user} -n ${project}")
    }

    def addSccToUser(user,scc, project) {
        script.sh("oc adm policy add-scc-to-user ${scc} -z ${user} -n ${project}")
    }

    def deployCodebase(project, templateName, codebase, imageName, timeout = null, parametersMap = null, values = null) {
        script.sh("oc -n ${project} process -f ${templateName} " +
                "-p IMAGE_NAME=${imageName} " +
                "-p APP_VERSION=${codebase.version} " +
                "-p NAMESPACE=${project} " +
                "--local=true -o json | oc -n ${project} apply -f -")
    }

    def createFullImageName(registryHost,ciProject,imageName) {
        return "${registryHost}/${ciProject}/${imageName}"
    }

}
