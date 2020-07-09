# EDP Library Pipelines Overview

EDP Library Pipelines repository - is a repository located in Git that stores all pipelines as a code. The main 
conception is realized on the [Jenkins Shared Libraries](https://www.jenkins.io/doc/book/pipeline/shared-libraries/) allowing to 
define the external pipeline source and then reuse the predefined code from the central storage. 

The EDP Library Pipelines repository describes the general structure of the code review, build, and deploy pipelines.
Every pipeline has a set of stages that are consumed from a pipeline`s parameters of a user 
and can be redefined as well. The realization of stages is described in the EDP Library Stages repository.  
 
The EDP Library Pipelines repository contains a structure and the execution subsequence of the stages parameters. 
The EDP Library Stages repository describes the specific steps and their realization in frames of a specific pipeline. 

If EDP pipelines are not enough for the CICD needs, it is possible to add a custom stage. To do this, a user 
creates the stage, adds it to the application repository, thus extending the EDP Pipelines Framework by customization, 
realization, and redefinition of the user stages. 
In such a case, the priority goes to the user stages.

### Related Articles

- [Add a New Custom Global Pipeline Library](documentation/add_new_custom_global_pipeline_lib.md)
- [Customize CI Pipeline](documentation/customize_ci_pipeline.md)

