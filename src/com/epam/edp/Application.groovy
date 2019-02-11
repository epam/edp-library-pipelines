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

import groovy.json.JsonSlurperClassic
import com.epam.edp.stages.ProjectType
import com.epam.edp.platform.Platform

class Application {
    Script script
    Platform platform

    def name
    def config = [:]

    Application(name, platform, script) {
        this.name = name
        this.script = script
        this.platform = platform
    }

    def setConfig(gerrit_autouser, gerrit_host, gerrit_sshPort, gerrit_project) {
        def componentSettings = null
        for(configMapKey in ["app.settings.json", "auto-test.settings.json"]) {
            componentSettings = findComponent(this.name, configMapKey)
            if (componentSettings != null) break
        }
        if (componentSettings == null)
            script.error("[JENKINS][ERROR] Component ${this.name} has not been found in configuration")
        componentSettings.cloneUrl = "ssh://${gerrit_autouser}@${gerrit_host}:${gerrit_sshPort}/${gerrit_project}"
        this.config = componentSettings
    }

    private def findComponent(nameToFind, configMapKey) {
        def configJson = platform.getJsonPathValue("cm","project-settings",".data.${configMapKey.replaceAll("\\.","\\\\.")}")
        def config = new JsonSlurperClassic().parseText(configJson)
        for (item in config) {
            if (item.name == nameToFind) {
                item.type = configMapKey == "app.settings.json" ? ProjectType.APPLICATION.getValue() : ProjectType.AUTOTESTS.getValue()
                return item

            }
        }
    }
}