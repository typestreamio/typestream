# Pipeline File Format

TypeStream pipelines-as-code use `.typestream.json` files. This page documents the complete file format.

## Top-level structure

```json
{
  "name": "pipeline-name",
  "version": "1",
  "description": "Optional description",
  "graph": {
    "nodes": [ ... ],
    "edges": [ ... ]
  }
}
```

| Field | Required | Type | Description |
|-------|----------|------|-------------|
| `name` | yes | string | Unique pipeline identifier |
| `version` | yes | string | Version string for tracking changes |
| `description` | no | string | Human-readable description |
| `graph` | yes | object | Pipeline graph definition |

## Graph

The graph contains nodes and edges forming a directed acyclic graph.

### Edges

```json
{ "fromId": "source-1", "toId": "filter-1" }
```

Each edge connects two nodes by their `id`.

### Nodes

Each node has an `id` (string) and exactly one node type field. See the [Node Reference](node-reference.md) for full details on each node type.

#### KafkaSource

```json
{
  "id": "source-1",
  "kafkaSource": {
    "topicPath": "/dev/kafka/local/topics/my_topic",
    "encoding": "AVRO",
    "unwrapCdc": false
  }
}
```

- `topicPath`: Virtual filesystem path to the Kafka topic (e.g. `/dev/kafka/local/topics/my_topic`)
- `encoding`: `"AVRO"` | `"JSON"`
- `unwrapCdc`: `true` to extract the `after` payload from Debezium CDC envelopes

#### PostgresSource

```json
{
  "id": "source-1",
  "postgresSource": {
    "topicPath": "/dev/kafka/local/topics/dbserver.public.orders"
  }
}
```

- `topicPath`: Virtual filesystem path to the Debezium CDC topic (CDC unwrapping is enabled automatically)

#### Filter

```json
{
  "id": "filter-1",
  "filter": {
    "expression": ".field == \"value\""
  }
}
```

- `expression`: A predicate expression (e.g. `.status_code == 200`, `.title ~= "Station"`)

#### GeoIp

```json
{
  "id": "geoip-1",
  "geoIp": {
    "ipField": "ip_address",
    "outputField": "country_code"
  }
}
```

#### TextExtractor

```json
{
  "id": "text-1",
  "textExtractor": {
    "filePathField": "file_url",
    "outputField": "extracted_text"
  }
}
```

#### EmbeddingGenerator

```json
{
  "id": "embed-1",
  "embeddingGenerator": {
    "textField": "title",
    "outputField": "embedding",
    "model": "text-embedding-3-small"
  }
}
```

#### OpenAiTransformer

```json
{
  "id": "ai-1",
  "openAiTransformer": {
    "prompt": "Summarize: ${title}",
    "outputField": "summary",
    "model": "gpt-4o-mini"
  }
}
```

#### MaterializedView

```json
{
  "id": "mv-1",
  "materializedView": {
    "groupByField": "status_code",
    "aggregationType": "count",
    "enableWindowing": false,
    "windowSizeSeconds": 0
  }
}
```

- `groupByField`: Field to group by
- `aggregationType`: `"count"` for counting, or `"latest"` for keeping the latest value per key
- `enableWindowing`: `true` to use tumbling time windows (only with `"count"` aggregation)
- `windowSizeSeconds`: Window size in seconds (when `enableWindowing` is `true`)

#### KafkaSink

```json
{
  "id": "sink-1",
  "kafkaSink": {
    "topicName": "output_topic"
  }
}
```

- `topicName`: Name of the output Kafka topic

#### ElasticsearchSink

```json
{
  "id": "sink-1",
  "elasticsearchSink": {
    "connectionId": "my-elasticsearch",
    "indexName": "documents",
    "documentIdStrategy": "RECORD_KEY",
    "writeMethod": "UPSERT",
    "behaviorOnNullValues": "IGNORE"
  }
}
```

#### WeaviateSink

```json
{
  "id": "sink-1",
  "weaviateSink": {
    "connectionId": "my-weaviate",
    "collectionName": "documents",
    "documentIdStrategy": "FieldIdStrategy",
    "documentIdField": "doc_id",
    "vectorStrategy": "FieldVectorStrategy",
    "vectorField": "embedding",
    "timestampField": "created_at"
  }
}
```

#### DbSink

```json
{
  "id": "sink-1",
  "dbSink": {
    "connectionId": "my-postgres",
    "tableName": "events",
    "insertMode": "upsert",
    "primaryKeyFields": "event_id"
  }
}
```

#### Inspector

```json
{
  "id": "inspector-1",
  "inspector": {
    "label": "debug tap"
  }
}
```

## Complete example

```json
{
  "name": "webvisits-enriched",
  "version": "1",
  "description": "Enrich web visits with geolocation from IP address",
  "graph": {
    "nodes": [
      {
        "id": "source-1",
        "kafkaSource": {
          "topicPath": "/dev/kafka/local/topics/web_visits",
          "encoding": "AVRO"
        }
      },
      {
        "id": "geoip-1",
        "geoIp": {
          "ipField": "ip_address",
          "outputField": "country_code"
        }
      },
      {
        "id": "filter-1",
        "filter": {
          "expression": ".country_code == \"US\""
        }
      },
      {
        "id": "sink-1",
        "kafkaSink": {
          "topicName": "us_visits_enriched"
        }
      }
    ],
    "edges": [
      { "fromId": "source-1", "toId": "geoip-1" },
      { "fromId": "geoip-1", "toId": "filter-1" },
      { "fromId": "filter-1", "toId": "sink-1" }
    ]
  }
}
```
