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
    }
}