<a name="unreleased"></a>
## [Unreleased]


<a name="v2.13.0"></a>
## [v2.13.0] - 2022-08-25
### Bug Fixes

- Use 'subresource' flag for patch codebasebranches [EPMDEDP-10122](https://jiraeu.epam.com/browse/EPMDEDP-10122)

### Routine

- Fix Jira Ticket pattern for changelog generator [EPMDEDP-10159](https://jiraeu.epam.com/browse/EPMDEDP-10159)
- Align build.gradle to new gradle version [EPMDEDP-10274](https://jiraeu.epam.com/browse/EPMDEDP-10274)
- Update changelog [EPMDEDP-8832](https://jiraeu.epam.com/browse/EPMDEDP-8832)

### BREAKING CHANGE:


Starting from this change, we expect that CodebaseBranches CRD use 'status' field as subresource


<a name="v2.12.0"></a>
## [v2.12.0] - 2022-05-17
### Features

- Implement makefile to generate changelog [EPMDEDP-8218](https://jiraeu.epam.com/browse/EPMDEDP-8218)
- Add normalizedBranch parameter for naming projects in sonar [EPMDEDP-8283](https://jiraeu.epam.com/browse/EPMDEDP-8283)
- Remove autodeploy/manual input creation from Job.groovy [EPMDEDP-8313](https://jiraeu.epam.com/browse/EPMDEDP-8313)
- v2 api switch [EPMDEDP-8383](https://jiraeu.epam.com/browse/EPMDEDP-8383)

### Bug Fixes

- Fix changelog generation in GH Release Action [EPMDEDP-8468](https://jiraeu.epam.com/browse/EPMDEDP-8468)
- Disable response log for httpRequests [EPMDEDP-8472](https://jiraeu.epam.com/browse/EPMDEDP-8472)

### Code Refactoring

- Wipe Create-release job workspace after execution [EPMDEDP-7683](https://jiraeu.epam.com/browse/EPMDEDP-7683)
- Remove unused functional [EPMDEDP-8168](https://jiraeu.epam.com/browse/EPMDEDP-8168)
- Remove deprecated functional [EPMDEDP-8168](https://jiraeu.epam.com/browse/EPMDEDP-8168)

### Routine

- Update release CI pipelines [EPMDEDP-7847](https://jiraeu.epam.com/browse/EPMDEDP-7847)
- Fix grammatical errors in Jenkins libraries [EPMDEDP-8205](https://jiraeu.epam.com/browse/EPMDEDP-8205)
- Update release template [EPMDEDP-8220](https://jiraeu.epam.com/browse/EPMDEDP-8220)
- Update changelog [EPMDEDP-9185](https://jiraeu.epam.com/browse/EPMDEDP-9185)

### BREAKING CHANGE:


Switch to use v2 admin console API for build, code review and deploy pipelines


<a name="v2.11.0"></a>
## [v2.11.0] - 2021-12-07
### Features

- Deploy application with dependency [EPMDEDP-7664](https://jiraeu.epam.com/browse/EPMDEDP-7664)

### Bug Fixes

- Create new branch from the defaultBranch [EPMDEDP-7552](https://jiraeu.epam.com/browse/EPMDEDP-7552)

### Routine

- Bump version to 2.11.0 [EPMDEDP-7847](https://jiraeu.epam.com/browse/EPMDEDP-7847)
- Align to release process [EPMDEDP-7847](https://jiraeu.epam.com/browse/EPMDEDP-7847)
- Add changelog [EPMDEDP-7847](https://jiraeu.epam.com/browse/EPMDEDP-7847)

### Documentation

- Updated the links on GitHub [EPMDEDP-7781](https://jiraeu.epam.com/browse/EPMDEDP-7781)


<a name="v2.10.0"></a>
## [v2.10.0] - 2021-12-06

<a name="v2.9.0"></a>
## [v2.9.0] - 2021-12-06

<a name="v2.8.2"></a>
## [v2.8.2] - 2021-12-06

<a name="v2.8.1"></a>
## [v2.8.1] - 2021-12-06

<a name="v2.8.0"></a>
## v2.8.0 - 2021-12-06
### Reverts

- [EPMDEDP-4971] Replace slash with dash for branch name


[Unreleased]: https://github.com/epam/edp-library-pipelines/compare/v2.13.0...HEAD
[v2.13.0]: https://github.com/epam/edp-library-pipelines/compare/v2.12.0...v2.13.0
[v2.12.0]: https://github.com/epam/edp-library-pipelines/compare/v2.11.0...v2.12.0
[v2.11.0]: https://github.com/epam/edp-library-pipelines/compare/v2.10.0...v2.11.0
[v2.10.0]: https://github.com/epam/edp-library-pipelines/compare/v2.9.0...v2.10.0
[v2.9.0]: https://github.com/epam/edp-library-pipelines/compare/v2.8.2...v2.9.0
[v2.8.2]: https://github.com/epam/edp-library-pipelines/compare/v2.8.1...v2.8.2
[v2.8.1]: https://github.com/epam/edp-library-pipelines/compare/v2.8.0...v2.8.1
