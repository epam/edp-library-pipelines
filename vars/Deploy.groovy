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

import com.epam.edp.Codebase
import com.epam.edp.Environment
import com.epam.edp.Job
import com.epam.edp.JobType
import com.epam.edp.Nexus
import com.epam.edp.Jenkins
import com.epam.edp.platform.PlatformType
import com.epam.edp.platform.PlatformFactory
import com.epam.edp.buildtool.BuildToolFactory
import com.epam.edp.stages.StageFactory
import org.apache.commons.lang.RandomStringUtils

def call() {
    def context = [:]
    node("master") {
        stage("Init") {
            context.platform = new PlatformFactory().getPlatformImpl(this)

            context.job = new Job(JobType.DEPLOY.value, context.platform, this)
            context.job.init()
            context.job.initDeployJob()
            println("[JENKINS][DEBUG] Created object job with type - ${context.job.type}")

            context.nexus = new Nexus(context.job, context.platform, this)
            context.nexus.init()

            context.jenkins = new Jenkins(context.job, context.platform, this)
            context.jenkins.init()

            context.factory = new StageFactory(script: this)
            context.factory.loadEdpStages().each() { context.factory.add(it) }
            context.factory.loadCustomStagesFromLib().each() { context.factory.add(it) }
            context.factory.loadCustomStages("${WORKSPACE.replaceAll("@.*", "")}@script/stages").each() { context.factory.add(it) }

            context.environment = new Environment(context.job.deployProject, context.platform, this)

            context.job.printDebugInfo(context)
            context.job.setDisplayName("${currentBuild.displayName}-${context.job.deployProject}")

            context.job.generateInputDataForDeployJob()
        }

        context.job.stages.each() { stage ->
            if (stage instanceof ArrayList) {
                def parallelStages = [:]
                stage.each() { parallelStage ->
                    parallelStages["${parallelStage.name}"] = {
                        context.stepName = parallelStage.step_name
                        context.job.runStage(parallelStage.name, context, parallelStage.step_name)
                    }
                }
                parallel parallelStages
            } else {
                context.stepName = stage.step_name
                context.job.runStage(stage.name, context, stage.step_name)
            }
        }

        if (currentBuild.currentResult == "SUCCESS"){
            office365ConnectorSend message:"<b>Success:</b> ${env.JOB_NAME} (${env.BUILD_NUMBER}) (<${env.BUILD_URL}|Open>)", color: "228B22", webhookUrl:'https://epam.webhook.office.com/webhookb2/440eb836-524d-44a4-b111-90a6c7830a41@b41b72d0-4e9f-4c26-8a69-f949f367c91d/JenkinsCI/7a9c50b9fd1542e8aec55da6263bb9b3/8a4d21be-ebf4-4513-85b6-f90382fc1eeb'
        }
        else{
            office365ConnectorSend message:"<b>Failed-catch:</b> ${env.JOB_NAME} (${env.BUILD_NUMBER}) (<${env.BUILD_URL}|Open>)", color: "FF0000", webhookUrl:'https://epam.webhook.office.com/webhookb2/440eb836-524d-44a4-b111-90a6c7830a41@b41b72d0-4e9f-4c26-8a69-f949f367c91d/JenkinsCI/7a9c50b9fd1542e8aec55da6263bb9b3/8a4d21be-ebf4-4513-85b6-f90382fc1eeb'
        }
    }
}