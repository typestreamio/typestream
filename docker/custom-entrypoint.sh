#!/bin/bash
# Custom entrypoint that registers the connector after Kafka Connect starts

# Run registration script in background
/usr/local/bin/register-connector.sh &

# Execute the original Debezium entrypoint
exec /docker-entrypoint.sh "$@"
