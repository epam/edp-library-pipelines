/* Copyright 2019 EPAM Systems.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at
http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.

See the License for the specific language governing permissions and
limitations under the License. */

def call() {
    def context = [:]
    node("master") {
        stage('Input parameters') {
            context.projectNames = input(id: 'Input', message: 'Input project names', ok: 'OK',
                    parameters: [
                            [$class                 : 'ValidatingStringParameterDefinition', defaultValue: '',
                             description            : 'Input comma separated projects list', name: 'PROJECT_NAMES',
                             regex                  : '[a-z0-9]([-a-z0-9]*[a-z0-9])?(,[a-z0-9]([-a-z0-9]*[a-z0-9])?)*',
                             failedValidationMessage: 'Incorrect list of projects']
                    ])
            try {
                assert context.projectNames ==~ /[a-z0-9]([-a-z0-9]*[a-z0-9])?(,[a-z0-9]([-a-z0-9]*[a-z0-9])?)*/
            }
            catch (AssertionError err) {
                error "[JENKINS][DEBUG] - Project list does not match requirements"
            }
        }

        stage('Delete projects') {
            openshift.withCluster() {
                context.projectNames.tokenize(',').each() { projectName ->
                    if (openshift.selector("project", projectName).exists()) {
                        openshiftDeleteResourceByKey apiURL: '', authToken: '', keys: "${projectName}", namespace: '', types: 'project', verbose: 'false'
                        sleep(10)
                        try {
                            sh("oc -n ${projectName} delete pod --all --force --grace-period=0")
                        }
                        catch (Exception ex) {
                            println("[JENKINS][DEBUG] Project ${projectName} removed")
                        }
                    } else {
                        error "[JENKINS][DEBUG] - Project ${projectName} not found"
                    }
                }
            }
        }
    }
}
