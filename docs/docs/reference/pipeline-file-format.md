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

#### StreamSource

```json
{
  "id": "source-1",
  "streamSource": {
    "dataStream": { "path": "/dev/kafka/local/topics/my_topic" },
    "encoding": "AVRO",
    "unwrapCdc": false
  }
}
```

- `encoding`: `"AVRO"` | `"JSON"`
- `unwrapCdc`: `true` to extract the `after` payload from Debezium CDC envelopes

#### Filter

```json
{
  "id": "filter-1",
  "filter": {
    "predicate": { "expr": ".field == \"value\"" },
    "byKey": false
  }
}
```

#### Group

```json
{
  "id": "group-1",
  "group": { "keyMapperExpr": ".country" }
}
```

#### Count

```json
{
  "id": "count-1",
  "count": {}
}
```

#### WindowedCount

```json
{
  "id": "wcount-1",
  "windowedCount": { "windowSizeSeconds": 60 }
}
```

#### ReduceLatest

```json
{
  "id": "reduce-1",
  "reduceLatest": {}
}
```

#### Join

```json
{
  "id": "join-1",
  "join": {
    "with": { "path": "/dev/kafka/local/topics/other_topic" },
    "joinType": { "byKey": true }
  }
}
```

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

#### Map

```json
{
  "id": "map-1",
  "map": { "mapperExpr": ".title" }
}
```

#### Each

```json
{
  "id": "each-1",
  "each": { "fnExpr": "record -> http post https://example.com ${record.id}" }
}
```

#### Sink

```json
{
  "id": "sink-1",
  "sink": {
    "output": { "path": "/dev/kafka/local/topics/output_topic" },
    "encoding": "AVRO"
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
        "streamSource": {
          "dataStream": { "path": "/dev/kafka/local/topics/web_visits" },
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
          "predicate": { "expr": ".country_code == \"US\"" }
        }
      },
      {
        "id": "sink-1",
        "sink": {
          "output": { "path": "/dev/kafka/local/topics/us_visits_enriched" },
          "encoding": "AVRO"
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
