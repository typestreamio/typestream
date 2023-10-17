#!/usr/bin/env bash

set -euo pipefail
IFS=$'\n\t'

gradlew -Pversion=beta -Djib.to.image=localhost:5000/typestream/server:beta :server:jibDockerBuild
gradlew -Pversion=beta -Djib.to.image=localhost:5000/typestream/tools-seeder:beta :tools:jibDockerBuild
