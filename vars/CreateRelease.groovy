/* Copyright 2019 EPAM Systems.

 Licensed under the Apache License, Version 2.0 (the "License");
 you may not use this file except in compliance with the License.
 You may obtain a copy of the License at
 http://www.apache.org/licenses/LICENSE-2.0

 Unless required by applicable law or agreed to in writing, software
 distributed under the License is distributed on an "AS IS" BASIS,
 WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.

 See the License for the specific language governing permissions and
 limitations under the License.*/

import com.epam.edp.Application
import com.epam.edp.Job
import com.epam.edp.JobType
import com.epam.edp.Gerrit
import com.epam.edp.Nexus
import com.epam.edp.Sonar
import com.epam.edp.platform.PlatformType
import com.epam.edp.platform.PlatformFactory
import com.epam.edp.buildtool.BuildToolFactory
import com.epam.edp.stages.StageFactory
import org.apache.commons.lang.RandomStringUtils

def call() {
    def context = [:]
    node("master") {
        stage("Init") {
            context.platform = new PlatformFactory().getPlatformImpl(PlatformType.OPENSHIFT, this)

            context.job = new Job(JobType.CREATERELEASE.value, context.platform, this)
            context.job.init()
            println("[JENKINS][DEBUG] Created object job with type - ${context.job.type}")

            context.gerrit = new Gerrit(context.job, context.platform, this)
            context.gerrit.init()

            context.application = new Application(context.gerrit.project, context.platform, this)
            context.application.setConfig(context.gerrit.autouser, context.gerrit.host, context.gerrit.sshPort, context.gerrit.project)

            context.factory = new StageFactory(script: this)
            context.factory.loadEdpStages().each() { context.factory.add(it) }

            context.job.printDebugInfo(context)
            println("[JENKINS][DEBUG] Application config - ${context.application.config}")
            context.job.setDisplayName("${currentBuild.number}-create-branch-${context.job.releaseName}")
            context.job.setDescription("Name: ${context.application.config.name}\r\nLanguage: ${context.application.config.language}" +
                    "\r\nBuild tool: ${context.application.config.build_tool}\r\nFramework: ${context.application.config.framework}")
        }

        context.workDir = new File("/tmp/${RandomStringUtils.random(10, true, true)}")
        context.workDir.deleteDir()

        context.triggerJobName = "Job-provisioning"
        context.triggerJobWait = true
        context.triggerJobParameters = [
                string(name: 'PARAM', value: "true"),
                string(name: 'NAME', value: "${context.application.config.name}"),
                string(name: 'TYPE', value: "${context.application.config.type}"),
                string(name: 'BUILD_TOOL', value: "${context.application.config.build_tool}"),
                string(name: 'BRANCH', value: "${context.job.releaseName}"),
        ]

        context.job.stages.each() { stage ->
            if (stage instanceof ArrayList) {
                def parallelStages = [:]
                stage.each() { parallelStage ->
                    parallelStages["${parallelStage.name}"] = {
                        context.job.runStage(parallelStage.name, context)
                    }
                }
                parallel parallelStages
            } else {
                context.job.runStage(stage.name, context)
            }
        }
    }
}