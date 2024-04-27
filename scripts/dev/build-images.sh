#!/usr/bin/env bash

set -euo pipefail
IFS=$'\n\t'

PLATFORM=${PLATFORM:-linux/amd64}

script_dir=$( cd -- "$( dirname -- "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )

"$script_dir"/../../gradlew -Pversion=beta -Djib.from.platforms="$PLATFORM" -Djib.to.image=localhost:5000/typestream/server:beta :server:jibDockerBuild
"$script_dir"/../../gradlew -Pversion=beta -Djib.from.platforms="$PLATFORM" -Djib.to.image=localhost:5000/typestream/tools-seeder:beta :tools:jibDockerBuild
