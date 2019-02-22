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

package com.epam.edp.buildtool

import com.epam.edp.Nexus
import org.apache.commons.lang.RandomStringUtils

class Maven implements BuildTool {
    Script script
    Nexus nexus

    def settings
    def hostedRepository
    def groupRepository
    def command

    def init() {
        this.settings = writeSettingsFile(this.script.libraryResource("maven/settings.xml"))
        this.hostedRepository = "${nexus.repositoriesUrl}/edp-maven"
        this.groupRepository = "${nexus.repositoriesUrl}/edp-maven-group"
        this.command = "mvn --settings ${this.settings} "
    }

    private writeSettingsFile(fileContent) {
         def settingsDir = new File("/tmp/${RandomStringUtils.random(10, true, true)}")
        settingsDir.deleteDir()
        script.writeFile file: "${settingsDir}/settings.xml", text: fileContent
        return("${settingsDir}/settings.xml")
    }
}