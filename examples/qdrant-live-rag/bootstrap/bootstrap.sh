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
#   4. apply the TypeStream pipeline  (CDC -> embed -> qdrantSink)
#      The server creates the Qdrant sink connector itself — the pipeline's
#      qdrantSink node is the connector, no manual registration needed.
#   5. wait for the server-created sink connector to be RUNNING  (target.sh)
#   6. wait for all seed docs to land in the collection          (target.sh)
# ---------------------------------------------------------------------------
set -euo pipefail

KAFKA_CONNECT=${KAFKA_CONNECT:-http://kafka-connect:8083}
SERVER_ADDR=${SERVER_ADDR:-server:4242}
QDRANT_REST=${QDRANT_REST:-http://qdrant:6333}
SCHEMA_REGISTRY=${SCHEMA_REGISTRY:-http://redpanda:8081}
CDC_TOPIC=${CDC_TOPIC:-dbserver.public.help_articles}
COLLECTION=${COLLECTION:-help_articles}
PIPELINE_FILE=${PIPELINE_FILE:-pipeline/help-articles.typestream.json}
# Connector name is deterministic: typestream-pipeline-<pipeline>-qdrant-sink-<node id>
SINK_CONNECTOR=${SINK_CONNECTOR:-typestream-pipeline-help-articles-rag-qdrant-sink-sink-1}
EXPECTED_DOCS=${EXPECTED_DOCS:-10}   # rows in db/01-help-articles.sql; bump if you change the seed
export KAFKA_CONNECT SERVER_ADDR QDRANT_REST SCHEMA_REGISTRY COLLECTION SINK_CONNECTOR

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

echo "[1/6] waiting for dependencies (kafka-connect, qdrant, server)..."
retry 120 "kafka-connect"     curl -sf "${KAFKA_CONNECT}/connectors"
retry 120 "qdrant"            curl -sf "${QDRANT_REST}/readyz"
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

echo "[5/6] applying TypeStream pipeline (server also creates the sink connector)..."
jq '{metadata: {name: .name, version: .version, description: .description}, graph: .graph}' \
  "${PIPELINE_FILE}" > /tmp/apply.json
grpcurl -plaintext -d @ "${SERVER_ADDR}" \
  io.typestream.grpc.PipelineService/ApplyPipeline < /tmp/apply.json | tee /tmp/apply-response.json
jq -e '.success == true' /tmp/apply-response.json >/dev/null || {
  echo "ApplyPipeline failed:" >&2; cat /tmp/apply-response.json >&2; exit 1
}
echo "  pipeline applied"
await_sink_connector

echo "[6/6] waiting for all ${EXPECTED_DOCS} docs to land in ${COLLECTION}..."
retry 120 "${EXPECTED_DOCS} docs in ${COLLECTION}" docs_ready
echo "  ${COLLECTION} now has $(collection_count) points"

echo ""
echo "Demo is live. Open http://localhost:8000 and ask: \"How long is the free trial?\""
