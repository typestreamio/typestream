#!/usr/bin/env bash

set -euo pipefail
IFS=$'\n\t'

script_dir=$( cd -- "$( dirname -- "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )

"$script_dir"/build-images.sh

docker push localhost:5000/typestream/server:beta
docker push localhost:5000/typestream/tools-seeder:beta
