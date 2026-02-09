#!/usr/bin/env bash
set -euo pipefail

REMOTE_HOST="${REMOTE_HOST:-typestream-demo}"
REMOTE_DIR="${REMOTE_DIR:-~/typestream}"

echo "==> Deploying demo to ${REMOTE_HOST}:${REMOTE_DIR}"

ssh "$REMOTE_HOST" bash -s "$REMOTE_DIR" <<'REMOTE_SCRIPT'
set -euo pipefail
REPO_DIR="$1"
cd "$REPO_DIR"

echo "==> Pulling latest code..."
git pull --ff-only

echo "==> Stopping existing containers..."
docker compose -f docker-compose.yml -f docker-compose.demo.yml down --remove-orphans

echo "==> Building server image (via Docker, no local Java needed)..."
docker run --rm \
  -v "$PWD":/workspace \
  -w /workspace \
  gradle:9-jdk21 \
  ./gradlew :server:jibBuildTar --image=typestream/server:latest
docker load -i server/build/jib-image.tar

echo "==> Rebuilding and starting all services..."
docker compose -f docker-compose.yml -f docker-compose.demo.yml up -d --build

echo "==> Waiting for Kafka Connect to be ready..."
until curl -sf http://localhost:8083/connectors > /dev/null 2>&1; do
  echo "  Kafka Connect not ready, retrying in 5s..."
  sleep 5
done

echo "==> Registering Debezium connector..."
# Delete existing connector if present (ignore errors)
curl -sf -X DELETE http://localhost:8083/connectors/postgres-cdc > /dev/null 2>&1 || true

curl -X POST http://localhost:8083/connectors \
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
      "value.converter.schema.registry.url": "http://redpanda:8081"
    }
  }'

echo ""
echo "==> Deploy complete!"
docker compose -f docker-compose.yml -f docker-compose.demo.yml ps
REMOTE_SCRIPT
