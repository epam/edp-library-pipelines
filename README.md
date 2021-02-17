# EDP Library Pipelines

EDP Library Pipelines repository - is a repository located in Git that stores all pipelines as a code. The main 
conception is realized on the [Jenkins Shared Libraries](https://www.jenkins.io/doc/book/pipeline/shared-libraries/) allowing to 
define the external pipeline source and then reuse the predefined code from the central storage. 

The EDP Library Pipelines repository describes the general structure of the code review, build, and deploy pipelines.
Every pipeline has a set of stages that are consumed from a pipeline`s parameters of a user 
and can be redefined as well. The realization of stages is described in the EDP Library Stages repository.  
 
The EDP Library Pipelines repository contains a structure and the execution subsequence of the stages parameters. 
The [EDP Library Stages](https://github.com/epam/edp-library-stages#edp-library-stages) repository describes the specific steps and their realization in frames of a specific pipeline. 

If EDP pipelines are not enough for the CICD needs, it is possible to add a custom stage. To do this, a user 
creates the stage, adds it to the application repository, thus extending the EDP Pipelines Framework by customization, 
realization, and redefinition of the user stages. 
In such a case, the priority goes to the user stages.

### Related Articles

- [Add a New Custom Global Pipeline Library](https://github.com/epam/edp-admin-console/blob/master/documentation/cicd_customization/add_new_custom_global_pipeline_lib.md#add-a-new-custom-global-pipeline-library)
- [Customize CI Pipeline](https://github.com/epam/edp-admin-console/blob/master/documentation/cicd_customization/customize_ci_pipeline.md#customize-ci-pipeline)

>_**NOTE**: To get more accurate information on the CI/CD customization, please refer to the [admin-console](https://github.com/epam/edp-admin-console/tree/master#edp-admin-console) repository._
