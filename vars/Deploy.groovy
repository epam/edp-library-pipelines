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


import com.epam.edp.Job
import com.epam.edp.JobType
import com.epam.edp.platform.PlatformFactory
import com.epam.edp.stages.StageFactory

def call() {

    def context = [:]

    node("master") {
        initGlobalContext(context)
        loadStages(context)
        runStages(context)
    }
}

def private initGlobalContext(context) {
    println("[JENKINS][DEBUG] initializing basic context")
    context.platform = new PlatformFactory().getPlatformImpl(this)
    context.job = new Job(JobType.DEPLOY.value, context.platform, this)
    context.job.init()
    println("[JENKINS][DEBUG] context has been initialized")
}

def private loadStages(context) {
    println("[JENKINS][DEBUG] loading library stages")
    context.factory = new StageFactory(script: this)
    context.factory.loadEdpStages().each() {
        context.factory.add(it)
    }
    context.factory.loadCustomStagesFromLib().each() {
        context.factory.add(it)
    }
    context.factory.loadCustomStages("${WORKSPACE.replaceAll("@.*", "")}@script/stages").each() {
        context.factory.add(it)
    }
    println("[JENKINS][DEBUG] stages have been loaded")
}

def private runStages(context) {
    println("[JENKINS][DEBUG] running stages")
    context.job.stages.each() { stage ->
        if (stage instanceof ArrayList) {
            def parallelStages = [:]
            stage.each() { parallelStage ->
                parallelStages["${parallelStage.step_name}"] = {
                    context.job.runStage(parallelStage.name, context, parallelStage.step_name)
                }
            }
            parallel parallelStages
        } else {
            context.job.runStage(stage.name, context, stage.step_name)
        }
    }
    println("[JENKINS][DEBUG] stages have been started")
}