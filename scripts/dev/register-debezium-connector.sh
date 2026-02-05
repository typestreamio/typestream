#!/usr/bin/env bash
set -euo pipefail

CONNECT_URL="${CONNECT_URL:-http://localhost:8083}"

echo "Waiting for Kafka Connect to be ready..."
until curl -sf "${CONNECT_URL}/connectors" > /dev/null 2>&1; do
  echo "  Kafka Connect not ready, retrying in 5s..."
  sleep 5
done

echo "Registering Debezium PostgreSQL connector..."
curl -X POST "${CONNECT_URL}/connectors" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "postgres-cdc",
    "config": {
      "connector.class": "io.debezium.connector.postgresql.PostgresConnector",
      "database.hostname": "postgres",
      "database.port": "5432",
      "database.user": "typestream",
      "database.password": "typestream",
      "database.dbname": "demo",
      "topic.prefix": "dbserver",
      "schema.include.list": "public",
      "table.include.list": "public.users,public.orders",
      "plugin.name": "pgoutput",
      "slot.name": "typestream_slot",
      "publication.name": "typestream_pub",
      "key.converter": "io.confluent.connect.avro.AvroConverter",
      "key.converter.schema.registry.url": "http://redpanda:8081",
      "value.converter": "io.confluent.connect.avro.AvroConverter",
      "value.converter.schema.registry.url": "http://redpanda:8081",
      "transforms": "extractKey",
      "transforms.extractKey.type": "org.apache.kafka.connect.transforms.ExtractField$Key",
      "transforms.extractKey.field": "id"
    }
  }'

echo ""
echo "Connector registered. Checking status..."
sleep 2
curl -s "${CONNECT_URL}/connectors/postgres-cdc/status" | jq .
