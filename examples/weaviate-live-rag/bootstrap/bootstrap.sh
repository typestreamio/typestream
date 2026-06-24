#!/usr/bin/env bash
# ---------------------------------------------------------------------------
# One-shot bootstrap (generic / reusable across targets).
#
# A fresh clone has empty Kafka state, so TypeStream has no pipeline to recover
# on first boot. This applies it once; every restart afterwards auto-recovers
# it (PipelineStateStore). Steps:
#   1. register the Postgres CDC source connector
#   2. wait for the CDC topic to carry data
#   3. create the destination collection            (target.sh)
#   4. apply the TypeStream pipeline  (CDC -> embed -> help_article_embeddings)
#   5. wait for the embeddings topic to carry data
#   6. register the destination sink connector       (target.sh)
# ---------------------------------------------------------------------------
set -euo pipefail

KAFKA_CONNECT=${KAFKA_CONNECT:-http://kafka-connect:8083}
SERVER_ADDR=${SERVER_ADDR:-server:4242}
WEAVIATE_REST=${WEAVIATE_REST:-http://weaviate:8080}
SCHEMA_REGISTRY=${SCHEMA_REGISTRY:-http://redpanda:8081}
CDC_TOPIC=${CDC_TOPIC:-dbserver.public.help_articles}
EMBEDDINGS_TOPIC=${EMBEDDINGS_TOPIC:-help_article_embeddings}
COLLECTION=${COLLECTION:-HelpArticle}
PIPELINE_FILE=${PIPELINE_FILE:-pipeline/help-articles.typestream.json}
export KAFKA_CONNECT SERVER_ADDR WEAVIATE_REST SCHEMA_REGISTRY EMBEDDINGS_TOPIC COLLECTION

# shellcheck source=target.sh
. ./target.sh

retry() { # retry <seconds> <description> <command...>
  local timeout=$1 desc=$2; shift 2
  local deadline=$(( $(date +%s) + timeout ))
  until "$@" >/dev/null 2>&1; do
    if [ "$(date +%s)" -ge "$deadline" ]; then
      echo "TIMED OUT waiting for: ${desc}" >&2; exit 1
    fi
    sleep 2
  done
}

subject_exists() { curl -sf "${SCHEMA_REGISTRY}/subjects" | jq -e --arg s "$1" 'index($s)' >/dev/null; }

echo "[1/6] waiting for dependencies (kafka-connect, weaviate, server)..."
retry 120 "kafka-connect"     curl -sf "${KAFKA_CONNECT}/connectors"
retry 120 "weaviate"          curl -sf "${WEAVIATE_REST}/v1/.well-known/ready"
retry 120 "server reflection" grpcurl -plaintext "${SERVER_ADDR}" list

echo "[2/6] registering Postgres CDC connector (public.help_articles)..."
curl -sf -X PUT "${KAFKA_CONNECT}/connectors/help-articles-cdc/config" \
  -H 'Content-Type: application/json' \
  -d '{
    "connector.class": "io.debezium.connector.postgresql.PostgresConnector",
    "database.hostname": "postgres",
    "database.port": "5432",
    "database.user": "typestream",
    "database.password": "typestream",
    "database.dbname": "demo",
    "topic.prefix": "dbserver",
    "schema.include.list": "public",
    "table.include.list": "public.help_articles",
    "plugin.name": "pgoutput",
    "slot.name": "help_articles_slot",
    "publication.name": "help_articles_pub",
    "key.converter": "io.confluent.connect.avro.AvroConverter",
    "key.converter.schema.registry.url": "http://redpanda:8081",
    "value.converter": "io.confluent.connect.avro.AvroConverter",
    "value.converter.schema.registry.url": "http://redpanda:8081"
  }' >/dev/null
echo "  done"

echo "[3/6] waiting for CDC snapshot to populate ${CDC_TOPIC}..."
retry 120 "${CDC_TOPIC} schema" subject_exists "${CDC_TOPIC}-value"
sleep 12   # let the server's filesystem (fsRefreshRate=10s) discover the topic

echo "[4/6] creating destination collection..."
create_collection

echo "[5/6] applying TypeStream pipeline..."
jq '{metadata: {name: .name, version: .version, description: .description}, graph: .graph}' \
  "${PIPELINE_FILE}" > /tmp/apply.json
grpcurl -plaintext -d @ "${SERVER_ADDR}" \
  io.typestream.grpc.PipelineService/ApplyPipeline < /tmp/apply.json
echo "  pipeline applied"

echo "[6/6] waiting for embeddings, then registering sink connector..."
retry 180 "${EMBEDDINGS_TOPIC} schema" subject_exists "${EMBEDDINGS_TOPIC}-value"
register_sink_connector

echo ""
echo "Demo is live. Open http://localhost:8000 and ask: \"How long is the free trial?\""
