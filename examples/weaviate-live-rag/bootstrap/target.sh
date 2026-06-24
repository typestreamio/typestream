#!/usr/bin/env bash
# ---------------------------------------------------------------------------
# TARGET-SPECIFIC SEAM (Weaviate).
# To retarget this demo to another datastore, this is one of only two files you
# rewrite (the other is chatbot/retriever.js). bootstrap.sh stays unchanged.
#
# Must define:
#   create_collection        - create the destination collection/schema (idempotent)
#   register_sink_connector  - register the Kafka Connect sink that drains
#                              $EMBEDDINGS_TOPIC into the destination
# Provided by bootstrap.sh:  $KAFKA_CONNECT, $WEAVIATE_REST, $SCHEMA_REGISTRY,
#                            $EMBEDDINGS_TOPIC, $COLLECTION
# ---------------------------------------------------------------------------

create_collection() {
  if curl -sf "${WEAVIATE_REST}/v1/schema/${COLLECTION}" >/dev/null 2>&1; then
    echo "  collection ${COLLECTION} already exists"
    return 0
  fi
  # vectorizer: none -> we supply pre-computed vectors from the pipeline.
  # Remaining properties are filled by Weaviate auto-schema on first object.
  curl -sf -X POST "${WEAVIATE_REST}/v1/schema" \
    -H 'Content-Type: application/json' \
    -d "{\"class\":\"${COLLECTION}\",\"vectorizer\":\"none\"}" >/dev/null
  echo "  created collection ${COLLECTION} (vectorizer: none)"
}

# Verbatim mirror of the config TypeStream's ConnectionService builds for the
# installed kafka-connect-weaviate v0.1.2 (server/.../ConnectionService.kt).
register_sink_connector() {
  curl -sf -X PUT "${KAFKA_CONNECT}/connectors/weaviate-sink/config" \
    -H 'Content-Type: application/json' \
    -d "{
      \"connector.class\": \"io.weaviate.connector.WeaviateSinkConnector\",
      \"tasks.max\": \"1\",
      \"topics\": \"${EMBEDDINGS_TOPIC}\",
      \"weaviate.connection.url\": \"${WEAVIATE_REST}\",
      \"weaviate.grpc.url\": \"weaviate:50051\",
      \"weaviate.grpc.secured\": \"false\",
      \"collection.mapping\": \"${COLLECTION}\",
      \"key.converter\": \"org.apache.kafka.connect.storage.StringConverter\",
      \"value.converter\": \"io.confluent.connect.avro.AvroConverter\",
      \"value.converter.schema.registry.url\": \"${SCHEMA_REGISTRY}\",
      \"document.id.strategy\": \"io.weaviate.connector.idstrategy.FieldIdStrategy\",
      \"document.id.field.name\": \"id\",
      \"vector.strategy\": \"io.weaviate.connector.vectorstrategy.FieldVectorStrategy\",
      \"vector.field.name\": \"embedding\"
    }" >/dev/null
  echo "  registered weaviate-sink connector (topic ${EMBEDDINGS_TOPIC} -> ${COLLECTION})"
}
