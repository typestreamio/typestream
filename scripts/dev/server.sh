#!/usr/bin/env bash

set -euo pipefail
IFS=$'\n\t'

script_dir=$( cd -- "$( dirname -- "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )

TYPESTREAM_CONFIG_PATH=$script_dir ./gradlew server:run -q --console=plain
