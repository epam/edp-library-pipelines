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
            context.job.initDeployJob()
            context.job.codebasesList.each() { item ->
                item.branch = "master"
                item.normalizedName = "${item.name}-master"
            }

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
            context.environment.setConfig()

            if (context.job.buildCause != "Image change") {
                def parameters = [string(
                        defaultValue: "${context.job.edpName}",
                        description: "Project prefix for stage ${context.job.deployProject} where services will be deployed.",
                        name: "PROJECT_PREFIX",
                        trim: true)]
                context.job.codebasesList.each() { codebase ->
                    codebase.tags = ['noImageExists']
                    def imageStreamExists = sh(
                            script: "oc -n ${context.job.metaProject} get is ${codebase.name}-master --no-headers | awk '{print \$1}'",
                            returnStdout: true
                    ).trim()
                    if (imageStreamExists != "")
                        codebase.tags = sh(
                                script: "oc -n ${context.job.metaProject} get is ${codebase.name}-master -o jsonpath='{range .spec.tags[*]}{.name}{\"\\n\"}{end}'",
                                returnStdout: true
                        ).trim().tokenize()

                    parameters.add(choice(choices: "${codebase.tags.join('\n')}", description: '', name: "${codebase.name.toUpperCase().replaceAll("-", "_")}_VERSION"))
                }
                context.job.userInputImagesToDeploy = input id: 'userInput', message: 'Provide the following information', parameters: parameters
                context.job.inputProjectPrefix = (context.job.userInputImagesToDeploy instanceof String) ? context.job.userInputImagesToDeploy : context.job.userInputImagesToDeploy["PROJECT_PREFIX"]

                if (context.job.inputProjectPrefix && context.job.inputProjectPrefix != context.job.edpName)
                    context.job.deployProject = "${context.job.edpName}-${context.job.inputProjectPrefix}-${context.job.stageWithoutPrefixName}"
            }

            context.job.codebasesList.each() { codebase ->
                if (context.job.userInputImagesToDeploy)
                    codebase.version = context.job.userInputImagesToDeploy["${codebase.name.toUpperCase().replaceAll("-", "_")}_VERSION"]
                codebase.version = codebase.version ? codebase.version : "latest"
            }

            if (!context.job.codebasesList)
                error("[JENKINS][ERROR] Environment ${context.job.stageName} is not found in project configs")

            context.job.printDebugInfo(context)
            context.job.setDisplayName("${currentBuild.displayName}-${context.job.deployProject}")

            context.job.runStage("Deploy", context)

            if (context.environment.updatedCodebases.isEmpty()) {
                println("[JENKINS][DEBUG] There are no codebase that have been updated, pipeline has stopped")
                return
            }

            context.environment.config.get('quality-gates').each() { qualityGate ->
                stage(qualityGate['step-name']) {
                    try {
                        switch (qualityGate.type) {
                            case "autotests":
                                context.autotest = new Codebase(context.job, qualityGate.project, context.platform, this)
                                context.autotest.setConfig(context.gerrit.autouser, context.gerrit.host, context.gerrit.sshPort, qualityGate.project)
                                println("[JENKINS][DEBUG] - ${context.autotest.config}")
                                node("${context.autotest.config.build_tool.toLowerCase()}") {
                                    context.workDir = new File("/tmp/${RandomStringUtils.random(10, true, true)}")
                                    context.workDir.deleteDir()

                                    context.buildTool = new BuildToolFactory().getBuildToolImpl(context.autotest.config.build_tool, this, context.nexus)
                                    context.buildTool.init()

                                    println("[JENKINS][DEBUG] - Config - ${context.autotest.config}")
                                    context.factory.getStage("automation-tests").run(context)
                                }
                                break
                            case "manual":
                                input "Is everything OK on project ${context.job.deployProject}?"
                                break
                        }
                    }
                    catch (Exception ex) {
                        context.job.setDescription("Stage ${qualityGate['step-name']} has been failed", true)
                        error("[JENKINS][ERROR] Stage ${qualityGate['step-name']} has been failed. Reason - ${ex}")
                    }
                }
                context.job.setDescription("Stage ${qualityGate['step-name']} has been passed")
            }

            if (context.job.userInputImagesToDeploy && context.job.inputProjectPrefix && context.job.inputProjectPrefix != context.job.edpName) {
                println("[JENKINS][WARNING] Promote images from custom projects is prohibited and will be skipped")
                return
            }
        }
    }
}