#!/usr/bin/env bash

set -euo pipefail
IFS=$'\n\t'

script_dir=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" &>/dev/null && pwd)

rpk container start

kafka_port=$(docker port "$(docker ps --filter name=rp-node-0 --format '{{.ID}}')" | grep 9093 | cut -d: -f2)
registry_port=$(docker port "$(docker ps --filter name=rp-node-0 --format '{{.ID}}')" | grep 8081 | cut -d: -f2)

{
  echo "[grpc]"
  echo "port=4242"
  echo "[sources.kafka.local]"
  echo "bootstrapServers=\"localhost:$kafka_port\""
  echo "schemaRegistry.url=\"http://localhost:$registry_port\""
  echo "fsRefreshRate=10"
} > "$script_dir"/typestream.toml

echo "export BOOTSTRAP_SERVER=localhost:$kafka_port"
