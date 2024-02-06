#!/usr/bin/env bash

set -euo pipefail
IFS=$'\n\t'


#!/usr/bin/env bash

set -euo pipefail
IFS=$'\n\t'

PLATFORM=${PLATFORM:-linux/amd64}

gradlew -Pversion=beta -Djib.from.platforms=$PLATFORM -Djib.to.image=localhost:5000/typestream/server:beta :server:jibDockerBuild
gradlew -Pversion=beta -Djib.from.platforms=$PLATFORM -Djib.to.image=localhost:5000/typestream/tools-seeder:beta :tools:jibDockerBuild