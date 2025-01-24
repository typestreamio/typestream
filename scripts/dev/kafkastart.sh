#!/usr/bin/env bash

set -euo pipefail
IFS=$'\n\t'

rpk container start --kafka-ports 9092 --schema-registry-ports 8081 --console-port 8080
