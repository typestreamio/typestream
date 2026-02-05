#!/bin/bash
# Script to register Debezium connector after Kafka Connect starts
# Runs in background while the main Connect process continues

set -e

CONNECT_URL="http://localhost:8083"

echo "Waiting for Kafka Connect to be ready..."
while ! curl -sf "${CONNECT_URL}/connectors" > /dev/null 2>&1; do
  sleep 2
done

echo "Kafka Connect is ready. Registering PostgreSQL CDC connector..."

# Check if connector already exists
if curl -sf "${CONNECT_URL}/connectors/postgres-cdc" > /dev/null 2>&1; then
  echo "Connector postgres-cdc already exists, skipping registration"
  exit 0
fi

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
      "table.include.list": "public.users,public.orders,public.file_uploads",
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
echo "Connector registered successfully"
