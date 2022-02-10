.DEFAULT_GOAL:=help
# set default shell
SHELL=/bin/bash -o pipefail -o errexit
help:  ## Display this help
	@awk 'BEGIN {FS = ":.*##"; printf "\nUsage:\n  make \033[36m<target>\033[0m\n"} /^[a-zA-Z0-9_-]+:.*?##/ { printf "  \033[36m%-15s\033[0m %s\n", $$1, $$2 } /^##@/ { printf "\n\033[1m%s\033[0m\n", substr($$0, 5) } ' $(MAKEFILE_LIST)

# use https://github.com/git-chglog/git-chglog/
.PHONY: changelog
changelog: ## generate changelog
ifneq (${NEXT_RELEASE_TAG},)
	@git-chglog --next-tag v${NEXT_RELEASE_TAG} -o CHANGELOG.md v2.7.0..
else
	@git-chglog -o CHANGELOG.md v2.7.0..
endif
