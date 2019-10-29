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

            context.environment = new Environment(context.job.deployProject, context.platform, this)

            context.job.printDebugInfo(context)
            context.job.setDisplayName("${currentBuild.displayName}-${context.job.deployProject}")

            context.job.generateInputDataForDeployJob()
        }

        context.job.runStage("deploy-helm", context)

        context.job.qualityGates.each() { qualityGate ->
            try {
                switch (qualityGate.qualityGateType) {
                    case "manual":
                        stage("${qualityGate.stepName}") {
                            input "Is everything OK on project ${context.job.deployProject}?"
                        }
                        break
                    case "autotests":
                        node("maven") {
                            println("Contents - ${qualityGate.autotest}")
                            context.buildTool = new BuildToolFactory().getBuildToolImpl(qualityGate.autotest.build_tool, this, context.nexus)
                            context.buildTool.init()

                            if (qualityGate.autotest.strategy == "import") {
                                context.job.gitProjectPath = qualityGate.autotest.gitProjectPath
                            }

                            context.job.setGitServerDataToJobContext(qualityGate.autotest.gitServer)

                            context.job.autotestName = qualityGate.autotest.name
                            context.job.testReportFramework = qualityGate.autotest.testReportFramework
                            context.job.autotestBranch = qualityGate.codebaseBranch.branchName
                            context.job.runStage("automation-tests", context, qualityGate.stepName)
                        }
                        break
                }
            }
            catch (Exception ex) {
                context.job.setDescription("Stage Quality gate ${qualityGate.stepName} for ${context.job.deployProject} has been failed", true)
                error("[JENKINS][ERROR] Stage Quality gate ${qualityGate.stepName} for ${context.job.deployProject} has been failed. Reason - ${ex}")
            }
        }
        context.job.promotion.sourceProject = context.job.metaProject

        context.job.runStage("Promote-images", context)
    }
}