#!/usr/bin/env bash
# ---------------------------------------------------------------------------
# TARGET-SPECIFIC SEAM (Qdrant).
# To retarget this demo to another datastore, this is one of only two files you
# rewrite (the other is chatbot/retriever.js). bootstrap.sh stays unchanged.
#
# Must define:
#   create_collection        - create the destination collection/schema (idempotent)
#   register_sink_connector  - register the Kafka Connect sink (or a no-op when the
#                              TypeStream server provisions it natively)
# Provided by bootstrap.sh:  $KAFKA_CONNECT, $QDRANT_REST, $SCHEMA_REGISTRY, $COLLECTION
#
# Unlike the Weaviate sibling, Qdrant is a first-class TypeStream sink: the pipeline
# declares a `qdrantSink` node and the server provisions the out-of-the-box Qdrant
# Kafka connector when the pipeline is applied. So register_sink_connector is a no-op
# here -- only the collection has to be pre-created (Qdrant has no auto-schema).
# ---------------------------------------------------------------------------

create_collection() {
  if curl -sf "${QDRANT_REST}/collections/${COLLECTION}" >/dev/null 2>&1; then
    echo "  collection ${COLLECTION} already exists"
    return 0
  fi
  # 1536 dims = OpenAI text-embedding-3-small; Cosine matches normalized embeddings.
  # We supply pre-computed vectors from the pipeline (no Qdrant-side vectorization).
  curl -sf -X PUT "${QDRANT_REST}/collections/${COLLECTION}" \
    -H 'Content-Type: application/json' \
    -d '{"vectors":{"size":1536,"distance":"Cosine"}}' >/dev/null
  echo "  created collection ${COLLECTION} (size 1536, Cosine)"
}

register_sink_connector() {
  # No-op: the TypeStream server provisions the Qdrant sink connector automatically
  # when the pipeline (with a qdrantSink node) is applied. The server reshapes each
  # record into Qdrant's {id, vector, payload} envelope and registers
  # io.qdrant.kafka.QdrantSinkConnector against the intermediate topic.
  echo "  sink connector handled by TypeStream server (qdrantSink node)"
}
