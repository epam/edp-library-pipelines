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
import org.apache.maven.artifact.versioning.*
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
            def sortedVersions = []
            def LATEST_TAG = "latest"
            def STABLE_TAG = "stable"

            context.job.codebasesList.each() { codebase ->
                codebase.tags = getCodebaseTags(codebase, context, codebase.inputIs)

                if (!codebase.tags.contains(LATEST_TAG)) {
                    codebase.tags += [LATEST_TAG]
                }

                if (!codebase.tags.contains(STABLE_TAG)) {
                    codebase.tags += [STABLE_TAG]
                }

                sortedVersions = sortTags(codebase.tags)

                outputIsVersions = getCodebaseTags(codebase, context, codebase.outputIs)
                sortedOutputIsVersions = sortTags(outputIsVersions)

                codebase.latest = getFirstTag(sortedVersions)
                codebase.stable = getFirstTag(sortedOutputIsVersions)

                if (codebase.stable == "noImageExists") {
                    sortedVersions -= [STABLE_TAG]
                }

                if (codebase.latest == "noImageExists") {
                    sortedVersions -= [LATEST_TAG]
                }

                println("Latest tag: ${codebase.latest}")
                println("Stable tag: ${codebase.stable}")

                sortedVersions = sortedVersions
                        .collect{tag -> tag.replaceAll(/^latest/, "${LATEST_TAG} (${codebase.latest})") }
                        .collect{tag -> tag.replaceAll(/^stable/, "${STABLE_TAG} (${codebase.stable})") }

                println("sorted Params: ${sortedVersions}")

                parameters.add(choice(choices: "${sortedVersions.join('\n')}", description: '', name: "${codebase.name.toUpperCase().replaceAll("-", "_")}_VERSION"))
            }
            context.job.userInputImagesToDeploy = input id: 'userInput', message: 'Provide the following information', parameters: parameters

            println("USERS_INPUT_IMAGES_TO_DEPLOY: ${context.job.userInputImagesToDeploy}")
            println(context.job.userInputImagesToDeploy.getClass())

            context.job.codebasesList.each() { codebase ->
                if (context.job.userInputImagesToDeploy instanceof java.lang.String) {
                    codebase.version = context.job.userInputImagesToDeploy
                    if (codebase.version.startsWith(LATEST_TAG))
                        codebase.version = LATEST_TAG
                    if (codebase.version.startsWith(STABLE_TAG))
                        codebase.version = STABLE_TAG
                }
                else {
                    context.job.userInputImagesToDeploy.each() { item ->
                        if (item.value.startsWith(LATEST_TAG)) {
                            context.job.userInputImagesToDeploy.put(item.key, LATEST_TAG)
                        }
                        if (item.value.startsWith(STABLE_TAG)) {
                            context.job.userInputImagesToDeploy.put(item.key, STABLE_TAG)
                        }
                    }
                    codebase.version = context.job.userInputImagesToDeploy["${codebase.name.toUpperCase().replaceAll("-", "_")}_VERSION"]
                }
                codebase.version = codebase.version ? codebase.version : LATEST_TAG
            }

            if (!context.job.codebasesList)
                error("[JENKINS][ERROR] Environment ${context.job.stageName} is not found in project configs")

            context.job.printDebugInfo(context)
            context.job.setDisplayName("${currentBuild.displayName}-${context.job.deployProject}")

            context.job.runStage("Deploy", context)

            try {
                switch (context.job.qualityGate) {
                    case "manual":
                        stage("${context.job.qualityGateName}") {
                            input "Is everything OK on project ${context.job.deployProject}?"
                        }
                        break
                    case "autotests":
                        node("maven") {
                            if (!context.job.stageAutotestsList.isEmpty()) {
                                context.job.stageAutotestsList.each() { item ->
                                    context.buildTool = new BuildToolFactory().getBuildToolImpl(item.buildTool, this, context.nexus)
                                    context.buildTool.init()
                                    context.job.autotestName = item.name
                                    context.job.testReportFramework = item.testReportFramework
                                    context.job.autotestBranch = item.branchName
                                    context.job.runStage("automation-tests", context)
                                }
                            }
                        }
                        break
                }
            }
            catch (Exception ex) {
                context.job.setDescription("Stage Quality gate for ${context.job.deployProject} has been failed", true)
                error("[JENKINS][ERROR] Stage Quality gate for ${context.job.deployProject} has been failed. Reason - ${ex}")
            }
            context.job.promotion.targetProject = context.job.metaProject
            context.job.promotion.sourceProject = context.job.metaProject
            context.job.runStage("Promote-images", context)
            println("[UPDATED CODEBASES] - ${context.environment.updatedCodebases}")

            if (context.environment.updatedCodebases.isEmpty()) {
                println("[JENKINS][DEBUG] There are no codebase that have been updated, pipeline has stopped")
                return
            }
        }
    }
}

def getCodebaseTags(codebase, context, is) {
    codebase.tags = ['noImageExists']
    def imageStreamExists = sh(
            script: "oc -n ${context.job.metaProject} get is ${is} --no-headers | awk '{print \$1}'",
            returnStdout: true
    ).trim()
    if (imageStreamExists != "")
        codebase.tags = sh(
                script: "oc -n ${context.job.metaProject} get is ${is} -o jsonpath='{range .spec.tags[*]}{.name}{\"\\n\"}{end}'",
                returnStdout: true
        ).trim().tokenize()
    def latestTag = codebase.tags.find { it == 'latest' }
    if (latestTag) {
        codebase.tags = codebase.tags.minus(latestTag)
        codebase.tags.add(0, latestTag)
    }
    if (codebase.tags != ['noImageExists']) {
        codebase.tags.add(0, "No deploy")
    }

    return codebase.tags
}

@NonCPS
def getFirstTag(tags) {
    def tag = tags.stream()
            .filter { it != "latest" }
            .filter { it != "stable" }
            .filter { it != "No deploy" }
            .findFirst()
            .get()

    return tag
}

@NonCPS
def sortTags(tags) {
    def map = ["latest": 2, "stable": 1, "No deploy": 3]

    return tags
            .collect { new ComparableVersion(it) }
            .sort { e1, e2 ->
        def res = map.getOrDefault(e1.toString(), 0) - map.getOrDefault(e2.toString(), 0)
        if (res == 0) {
            e1.compareTo(e2)
        }
        res
    }
    .collect { item -> item.toString() }
            .reverse()
}