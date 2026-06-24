---
description: Generate vector embeddings and sink them to a Qdrant collection for semantic search.
---

# Sink to Qdrant

This guide shows how to stream pre-computed embeddings into a [Qdrant](https://qdrant.tech)
collection using a first-class `qdrantSink` node. TypeStream reshapes each record into
Qdrant's `{id, vector, payload}` envelope and provisions the out-of-the-box
[`io.qdrant.kafka.QdrantSinkConnector`](https://github.com/qdrant/qdrant-kafka) automatically.

## Prerequisites

- TypeStream [installed](../installation.mdx) and running
- A Qdrant instance reachable from the TypeStream server **and** from Kafka Connect
- An OpenAI API key set as `OPENAI_API_KEY` on the server (for embedding generation)
- The collection created in advance (Qdrant has no auto-schema) with the right vector size
  and distance, e.g. for `text-embedding-3-small`:

  ```bash
  curl -X PUT "$QDRANT_URL/collections/help_articles" \
    -H 'Content-Type: application/json' \
    -d '{"vectors":{"size":1536,"distance":"Cosine"}}'
  ```

## Register a Qdrant connection

Register your Qdrant instance with TypeStream so the server can resolve it by id. A
`dev-qdrant` connection is auto-registered from `TYPESTREAM_QDRANT_GRPC_URL` (default
`http://localhost:6334`); register additional ones via the `ConnectionService` gRPC API
(`RegisterQdrantConnection`).

The server must also be able to reach Kafka Connect — set `KAFKA_CONNECT_URL`
(e.g. `http://kafka-connect:8083`), since it creates the connector itself.

## Build the pipeline

Read from a Kafka topic, generate embeddings from a text field, and sink to Qdrant.

```json
{
  "name": "help-articles-rag",
  "version": "1",
  "description": "Embed help_articles and index them in Qdrant",
  "graph": {
    "nodes": [
      {
        "id": "source-1",
        "kafkaSource": {
          "topicPath": "/dev/kafka/local/topics/dbserver.public.help_articles",
          "encoding": "AVRO",
          "unwrapCdc": true
        }
      },
      {
        "id": "embed-1",
        "embeddingGenerator": {
          "textField": "body",
          "outputField": "embedding",
          "model": "text-embedding-3-small"
        }
      },
      {
        "id": "sink-1",
        "qdrantSink": {
          "connectionId": "dev-qdrant",
          "collectionName": "help_articles",
          "idField": "id",
          "vectorField": "embedding"
        }
      }
    ],
    "edges": [
      { "fromId": "source-1", "toId": "embed-1" },
      { "fromId": "embed-1", "toId": "sink-1" }
    ]
  }
}
```

Apply it with `typestream apply` (or the `PipelineService/ApplyPipeline` RPC). On apply, the
server desugars `qdrantSink` into a reshape step + a JSON sink, then registers the Qdrant
connector against the intermediate topic.

## Qdrant sink configuration

| Field | Description |
|-------|-------------|
| `connection_id` | Registered Qdrant connection (e.g. `dev-qdrant`) |
| `collection_name` | Target collection (must already exist with a matching vector size) |
| `id_field` | Record field used as the point id — integer or UUID (default `id`) |
| `vector_field` | Record field holding the embedding vector (default `embedding`) |

## How it works

Qdrant's connector expects a fixed JSON envelope and has no field-mapping config, so TypeStream
does the reshaping server-side:

```
record {id, title, body, embedding}
   -> reshape  {id, vector: <embedding>, payload: {title, body, ...}}
   -> JSON sink (intermediate topic)
   -> QdrantSinkConnector (JsonConverter, schemas.enable=false)
   -> Qdrant collection (qdrant.collection.name)
```

All fields other than `id_field` and `vector_field` become the point payload. The collection
name is supplied via the connector's `qdrant.collection.name`, so records carry no
`collection_name`.

## Schema behavior

The Embedding Generator adds `outputField` (a list of floats) to the schema and validates that
`textField` exists. The reshape step then emits `{id, vector, payload}` and forces JSON output
encoding for the sink.

## See also

- [Add Semantic Search](add-semantic-search.md) — the Weaviate equivalent (connector registered by hand)
- [Node Reference: EmbeddingGenerator](../reference/node-reference.md#embeddinggenerator)
- [`examples/qdrant-live-rag`](https://github.com/typestreamio/typestream/tree/main/examples/qdrant-live-rag) — a runnable end-to-end demo
