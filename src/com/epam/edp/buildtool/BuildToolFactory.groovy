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

import com.epam.edp.Job
import com.epam.edp.Nexus

def getBuildToolImpl(builtTool, script, Nexus nexus, Job job) {
    switch (builtTool.toLowerCase()) {
        case BuildToolType.MAVEN.value:
            return new Maven(script: script, nexus: nexus, job: job)
        case BuildToolType.NPM.value:
            return new Npm(script: script, nexus: nexus, job: job)
        case BuildToolType.GRADLE.value:
            return new Gradle(script: script, nexus: nexus, job: job)
        case BuildToolType.DOTNET.value:
            return new Dotnet(script: script, nexus: nexus, job: job)
        case BuildToolType.PYTHON.value:
            return new Python(script: script, nexus: nexus, job: job)
        case BuildToolType.GO.value:
            return new Go(script: script, nexus: nexus, job: job)
        default:
            return new Any(script: script, nexus: nexus, job: job)
    }
}

interface BuildTool {
    def init()
}