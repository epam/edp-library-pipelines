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

class Openshift implements Platform {
    Script script

    def getJsonPathValue(object, name, jsonPath) {
        script.sh(
                script: "oc get ${object} ${name} -o jsonpath='{${jsonPath}}'",
                returnStdout: true
        ).trim()
    }

    def getJsonValue(object, name) {
        script.sh(
                script: "oc get ${object} ${name} -o json",
                returnStdout: true
        ).trim()
    }
}