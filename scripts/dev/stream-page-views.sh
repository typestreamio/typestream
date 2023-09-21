#!/usr/bin/env bash

set -euo pipefail
IFS=$'\n\t'

script_dir=$( cd -- "$( dirname -- "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )

./gradlew tools:run --args="$script_dir/server.properties stream-page-views" -q --console=plain
