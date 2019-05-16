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

import com.epam.edp.Application
import com.epam.edp.Environment
import com.epam.edp.Gerrit
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
            context.platform = new PlatformFactory().getPlatformImpl(PlatformType.OPENSHIFT, this)

            context.job = new Job(JobType.DEPLOY.value, context.platform, this)
            context.job.init()
            context.job.initDeployV2Job()
            println("[JENKINS][DEBUG] Created object job with type - ${context.job.type}")

            context.nexus = new Nexus(context.job, context.platform, this)
            context.nexus.init()

            context.jenkins = new Jenkins(context.job, context.platform, this)
            context.jenkins.init()

            context.gerrit = new Gerrit(context.job, context.platform, this)
            context.gerrit.init()

            context.factory = new StageFactory(script: this)
            context.factory.loadEdpStages().each() { context.factory.add(it) }

            context.environment = new Environment(context.job.deployProject, context.platform, this)

            def parameters = []

            context.job.applicationsList.each() { application ->
                application.tags = ['noImageExists']
                def imageStreamExists = sh(
                        script: "oc -n ${context.job.metaProject} get is ${application.inputIs} --no-headers | awk '{print \$1}'",
                        returnStdout: true
                ).trim()
                if (imageStreamExists != "")
                    application.tags = sh(
                            script: "oc -n ${context.job.metaProject} get is ${application.inputIs} -o jsonpath='{range .spec.tags[*]}{.name}{\"\\n\"}{end}'",
                            returnStdout: true
                    ).trim().tokenize()
                def latestTag = application.tags.find { it == 'latest' }
                if (latestTag) {
                    application.tags = application.tags.minus(latestTag)
                    application.tags.add(0, latestTag)
                }

                parameters.add(choice(choices: "${application.tags.join('\n')}", description: '', name: "${application.name.toUpperCase().replaceAll("-", "_")}_VERSION"))
            }
            context.job.userInputImagesToDeploy = input id: 'userInput', message: 'Provide the following information', parameters: parameters

            context.job.applicationsList.each() { application ->
                if (context.job.userInputImagesToDeploy instanceof java.lang.String)
                    application.version = context.job.userInputImagesToDeploy
                else
                    application.version = context.job.userInputImagesToDeploy["${application.name.toUpperCase().replaceAll("-", "_")}_VERSION"]
                application.version = application.version ? application.version : "latest"
            }

            if (!context.job.applicationsList)
                error("[JENKINS][ERROR] Environment ${context.job.stageName} is not found in project configs")

            context.job.printDebugInfo(context)
            context.job.setDisplayName("${currentBuild.displayName}-${context.job.deployProject}")

            context.job.runStage("Deploy", context)
            stage("${context.job.qualityGateName}") {
                try {
                    switch (context.job.qualityGate) {
                        case "manual":
                            input "Is everything OK on project ${context.job.deployProject}?"
                            break
                    }
                }
                catch (Exception ex) {
                    context.job.setDescription("Stage Quality gate for ${context.job.deployProject} has been failed", true)
                    error("[JENKINS][ERROR] Stage Quality gate for ${context.job.deployProject} has been failed. Reason - ${ex}")
                }
            }
            context.job.promotion.targetProject = context.job.metaProject
            context.job.promotion.sourceProject = context.job.metaProject
            context.job.runStage("Promote-images", context)
            println("[UPDATED APPLICATIONS] - ${context.environment.updatedApplicaions}")

            if (context.environment.updatedApplicaions.isEmpty()) {
                println("[JENKINS][DEBUG] There are no application that have been updated, pipeline has stopped")
                return
            }
        }
    }
}