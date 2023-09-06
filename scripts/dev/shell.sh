#!/usr/bin/env bash

set -euo pipefail
IFS=$'\n\t'

script_dir=$( cd -- "$( dirname -- "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )

cd "$script_dir"/../cli

go run main.go
