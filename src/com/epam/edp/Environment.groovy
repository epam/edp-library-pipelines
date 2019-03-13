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
import groovy.json.JsonSlurperClassic

class Environment {
    Script script
    Platform platform

    def name
    def config = [:]
    def version = ""
    def updatedApplicaions = []

    Environment(name, platform, script) {
        this.name = name
        this.script = script
        this.platform = platform
    }

    def setConfig() {
        def componentSettings = findEnvironment(this.name, "env.settings.json")
        if (componentSettings == null)
            script.error("[JENKINS][ERROR] Environment ${this.name} has not been found in configuration")
        this.config = componentSettings
    }

    private def findEnvironment(environmentToFind, configMapKey) {
        def configJson = platform.getJsonPathValue("cm","project-settings",".data.${configMapKey.replaceAll("\\.","\\\\.")}")
        def config = new JsonSlurperClassic().parseText(configJson)
        for (item in config) {
            if (item.name == environmentToFind)
                return item
        }
        return null
    }
}