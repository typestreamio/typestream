---
description: Write pipeline output to an Elasticsearch index.
---

# Sink to Elasticsearch

This guide shows how to create a pipeline that writes streaming data to an Elasticsearch index for full-text search and analytics.

## Prerequisites

- TypeStream [installed](../installation.mdx) and running
- An Elasticsearch instance accessible from the TypeStream server

## Register an Elasticsearch connection

Before creating the pipeline, register your Elasticsearch instance. In the GUI, navigate to **Connections > Elasticsearch** and add:

- **URL**: Your Elasticsearch endpoint (e.g. `http://elasticsearch:9200`)
- **Credentials**: Username and password (if authentication is enabled)

The server monitors connection health and the connection appears as a sink option in the graph builder.

## Build the pipeline

import Tabs from "@theme/Tabs";
import TabItem from "@theme/TabItem";

<Tabs>
  <TabItem value="gui" label="GUI" default>

1. Drag a **Kafka Source** and select your topic
2. Optionally add transform or enrichment nodes
3. Drag an **Elasticsearch Sink** from the palette (appears under Database Sinks after registering a connection)
   - Set the index name
   - Configure the document ID strategy and write method
4. Click **Create Job**

  </TabItem>
  <TabItem value="config" label="Config-as-Code">

```json
{
  "name": "books-to-elasticsearch",
  "version": "1",
  "description": "Index books into Elasticsearch",
  "graph": {
    "nodes": [
      {
        "id": "source-1",
        "streamSource": {
          "dataStream": { "path": "/dev/kafka/local/topics/books" },
          "encoding": "AVRO"
        }
      },
      {
        "id": "sink-1",
        "sink": {
          "output": { "path": "/dev/kafka/local/topics/books_es_intermediate" }
        }
      }
    ],
    "edges": [
      { "fromId": "source-1", "toId": "sink-1" }
    ]
  }
}
```

:::note
In config-as-code, the Elasticsearch sink connector is created separately via the `ConnectionService` gRPC API. The pipeline writes to an intermediate Kafka topic, and a Kafka Connect sink connector forwards records to Elasticsearch.
:::

  </TabItem>
</Tabs>

## Elasticsearch sink configuration

| Field | Description |
|-------|-------------|
| `index_name` | Elasticsearch index to write to |
| `document_id_strategy` | How to derive the document `_id` from records |
| `write_method` | Write behavior: `INSERT` or `UPSERT` |
| `behavior_on_null_values` | How to handle null field values |

## How it works

Under the hood, TypeStream creates a Kafka Connect Elasticsearch sink connector. The pipeline writes processed records to an intermediate Kafka topic, and the connector forwards them to Elasticsearch. Credentials are resolved server-side from the registered connection -- they never appear in pipeline definitions.

## See also

- [Node Reference: Sink](../reference/node-reference.md#sink) -- sink node configuration
- [Geo-Enrich Events](geo-enrich-events.md) -- enrich data before indexing
