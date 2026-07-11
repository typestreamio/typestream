#!/usr/bin/env bash
# ---------------------------------------------------------------------------
# TARGET-SPECIFIC SEAM (Qdrant).
# To retarget this demo to another datastore, this is one of only two files you
# rewrite (the other is chatbot/retriever.js). bootstrap.sh stays unchanged.
#
# Must define:
#   create_collection     - create the destination collection/schema (idempotent)
#   await_sink_connector  - wait until the sink connector is RUNNING. The
#                           pipeline's qdrantSink node makes the TypeStream
#                           server register the connector during ApplyPipeline,
#                           so there is nothing to register here — just wait.
#   collection_count      - number of documents currently in the collection
#   docs_ready            - success when collection_count >= $EXPECTED_DOCS
# Provided by bootstrap.sh:  $KAFKA_CONNECT, $QDRANT_REST, $SCHEMA_REGISTRY,
#                            $COLLECTION, $SINK_CONNECTOR, $EXPECTED_DOCS
# ---------------------------------------------------------------------------

# Vector size must match the pipeline's embedding model
# (text-embedding-3-small -> 1536 dimensions).
VECTOR_SIZE=${VECTOR_SIZE:-1536}

create_collection() {
  if curl -sf "${QDRANT_REST}/collections/${COLLECTION}" >/dev/null 2>&1; then
    echo "  collection ${COLLECTION} already exists"
    return 0
  fi
  # qdrant-kafka does not auto-create collections; dimensions and distance
  # must be configured up front.
  curl -sf -X PUT "${QDRANT_REST}/collections/${COLLECTION}" \
    -H 'Content-Type: application/json' \
    -d "{\"vectors\":{\"size\":${VECTOR_SIZE},\"distance\":\"Cosine\"}}" >/dev/null
  echo "  created collection ${COLLECTION} (size ${VECTOR_SIZE}, distance Cosine)"
}

sink_connector_running() {
  curl -sf "${KAFKA_CONNECT}/connectors/${SINK_CONNECTOR}/status" \
    | jq -e '.connector.state == "RUNNING" and (.tasks[0].state == "RUNNING")' >/dev/null
}

await_sink_connector() {
  retry 120 "sink connector ${SINK_CONNECTOR} RUNNING" sink_connector_running
  echo "  sink connector ${SINK_CONNECTOR} is running"
}

collection_count() {
  curl -s "${QDRANT_REST}/collections/${COLLECTION}" \
    | jq -r '.result.points_count // 0'
}

docs_ready() { [ "$(collection_count)" -ge "${EXPECTED_DOCS}" ]; }
