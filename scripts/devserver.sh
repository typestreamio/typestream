#!/usr/bin/env bash

set -euo pipefail
IFS=$'\n\t'

script_dir=$( cd -- "$( dirname -- "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )

./gradlew server:run --args="$script_dir/server.dev.properties" -q --console=plain
