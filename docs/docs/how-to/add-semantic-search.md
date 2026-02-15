---
description: Generate vector embeddings and sink them to Weaviate for semantic search.
---

# Add Semantic Search

This guide shows how to build a pipeline that generates vector embeddings from text and writes them to a Weaviate vector database, enabling semantic search over your streaming data.

## Prerequisites

- TypeStream [installed](../installation.mdx) and running
- A Weaviate instance accessible from the TypeStream server
- An OpenAI API key set as `OPENAI_API_KEY` on the server (for embedding generation)

## Register a Weaviate connection

Before creating the pipeline, register your Weaviate instance with TypeStream. In the GUI, navigate to **Connections > Weaviate** and add your connection details (URL and optional API key).

The connection will appear as a sink option in the graph builder palette.

## Build the pipeline

The full flow: read from a Kafka topic, generate embeddings from a text field, and write to Weaviate.

import Tabs from "@theme/Tabs";
import TabItem from "@theme/TabItem";

<Tabs>
  <TabItem value="gui" label="GUI" default>

1. Drag a **Kafka Source** and select your topic
2. Drag an **Embedding Generator** node and connect it
   - Set `textField` to the field containing your text (e.g. `title`)
   - Set `outputField` to `embedding`
   - Choose a model (e.g. `text-embedding-3-small`)
3. Drag a **Weaviate Sink** from the palette (appears under Vector Sinks after registering a connection)
   - Set the collection name
   - Configure the document ID strategy and vector strategy
4. Click **Create Job**

  </TabItem>
  <TabItem value="config" label="Config-as-Code">

```json
{
  "name": "books-semantic-search",
  "version": "1",
  "description": "Generate embeddings for books and index in Weaviate",
  "graph": {
    "nodes": [
      {
        "id": "source-1",
        "kafkaSource": {
          "topicPath": "/local/topics/books",
          "encoding": "AVRO"
        }
      },
      {
        "id": "embed-1",
        "embeddingGenerator": {
          "textField": "title",
          "outputField": "embedding",
          "model": "text-embedding-3-small"
        }
      },
      {
        "id": "sink-1",
        "kafkaSink": {
          "topicName": "books_embeddings"
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

:::note
In config-as-code, the Weaviate sink connector is created separately via the `ConnectionService` gRPC API. The pipeline writes to an intermediate Kafka topic, and a Kafka Connect sink connector forwards records to Weaviate.
:::

  </TabItem>
</Tabs>

## Weaviate sink configuration

| Field | Description |
|-------|-------------|
| `collection_name` | Weaviate collection to write to |
| `document_id_strategy` | How to derive the document ID from records |
| `vector_strategy` | How to map the embedding field to the Weaviate vector |
| `timestamp_field` | Optional field to use as the record timestamp |

## Schema behavior

The Embedding Generator adds `outputField` (type: list of floats) to the output schema. It validates at compile time that `textField` exists in the input schema.

## See also

- [Node Reference: EmbeddingGenerator](../reference/node-reference.md#embeddinggenerator) -- full node specification
- [Enrich with AI](enrich-with-ai.md) -- use LLM prompts to transform data before embedding
