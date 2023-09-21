#!/usr/bin/env bash

set -euo pipefail
IFS=$'\n\t'

script_dir=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" &>/dev/null && pwd)

rpk container start

kafka_port=$(docker port "$(docker ps --filter name=rp-node-0 --format '{{.ID}}')" | grep 9093 | cut -d: -f2)
registry_port=$(docker port "$(docker ps --filter name=rp-node-0 --format '{{.ID}}')" | grep 8081 | cut -d: -f2)

{
  echo "grpc.port=4242"
  echo "sources.kafka=local"
  echo "sources.kafka.local.bootstrapServers=localhost:$kafka_port"
  echo "sources.kafka.local.schemaRegistryUrl=http://localhost:$registry_port"
  echo "sources.kafka.local.fsRefreshRate=10"
} > "$script_dir"/server.properties

echo "export BOOTSTRAP_SERVER=localhost:$kafka_port"
