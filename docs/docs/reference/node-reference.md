# Node Reference

TypeStream pipelines are built from **nodes** -- composable units that source, transform, enrich, aggregate, or sink data. All 18 node types are documented here, organized by category.

Every node implements `inferOutputSchema()`, which lets TypeStream propagate schema information through the pipeline at compile time -- before any Kafka Streams resources are allocated. This is how the GUI shows field names on every edge and validates configurations before execution.

## Source Nodes

Source nodes read data from Kafka topics and produce a typed data stream.

### StreamSource

Reads from a Kafka topic and resolves the schema from Schema Registry.

| Field | Type | Description |
|-------|------|-------------|
| `dataStream` | path | Kafka topic path (e.g. `/dev/kafka/local/topics/web_visits`) |
| `encoding` | `AVRO` \| `JSON` | Record encoding format |
| `unwrapCdc` | boolean | If `true`, extracts the `after` payload from a Debezium CDC envelope |

**Schema behavior**: Looks up the topic schema from Schema Registry via the catalog. When `unwrapCdc=true`, the schema is unwrapped to the inner `after` struct -- so downstream nodes see the table columns directly, not the CDC envelope.

**Supported in**: CLI DSL (`cat`/`grep`), config-as-code, GUI (Kafka Source / Postgres Source)

### ShellSource

A virtual source used internally by the interactive shell. Not available in config-as-code or the GUI.

| Field | Type | Description |
|-------|------|-------------|
| `data` | list of DataStreams | Data streams bound by the shell session |

**Schema behavior**: Uses the first data stream's schema. Always JSON encoding.

**Supported in**: CLI interactive shell only

## Transform Nodes

Transform nodes modify, filter, or reshape records flowing through the pipeline.

### Filter

Selects records that match a predicate expression.

| Field | Type | Description |
|-------|------|-------------|
| `predicate` | expression | A predicate expression (e.g. `.country == "US"`) |
| `byKey` | boolean | If `true`, filter matches against the record key instead of the value |

**Schema behavior**: Pass-through (schema unchanged).

**DSL equivalent**: `grep [pattern]` or `grep *(.field == "value")`

**Supported in**: CLI DSL, config-as-code, GUI

### Map

Transforms each record by applying a mapper expression.

| Field | Type | Description |
|-------|------|-------------|
| `mapper` | expression | A transformation expression applied to each record |

**Schema behavior**: Pass-through. Accurate field-level schema tracking is a TODO.

**DSL equivalent**: `cut .field1 .field2`

**Supported in**: CLI DSL, config-as-code

### Group

Re-keys records by a field expression, preparing them for downstream aggregation.

| Field | Type | Description |
|-------|------|-------------|
| `keyMapperExpr` | expression | Expression to extract the new key (e.g. `.country`) |

**Schema behavior**: Pass-through (re-keys records but doesn't change the value schema).

**Supported in**: Config-as-code, GUI (as part of Materialized View)

### Join

Joins two data streams by key.

| Field | Type | Description |
|-------|------|-------------|
| `with` | path | The second stream to join with |
| `joinType` | `byKey` | Join strategy (currently key-based only) |

**Schema behavior**: Merges left and right schemas into a combined struct. The output contains all fields from both streams.

**DSL equivalent**: `join /dev/kafka/local/topics/other_topic`

**Supported in**: CLI DSL, config-as-code

### Each

Executes a side-effect expression for each record. Does not modify the stream.

| Field | Type | Description |
|-------|------|-------------|
| `fn` | block expression | Code to execute per record |

**Schema behavior**: Pass-through (side-effect only).

**DSL equivalent**: `each { record -> ... }`

**Supported in**: CLI DSL

### NoOp

A structural placeholder node. Used internally as the root node of a pipeline graph.

**Schema behavior**: Pass-through.

## Aggregation Nodes

Aggregation nodes reduce a stream into a stateful result. They typically follow a Group node.

### Count

Counts records per key into a KTable.

**Schema behavior**: Pass-through (the count result is stored as the value).

**DSL equivalent**: `wc`

**Supported in**: CLI DSL, config-as-code, GUI (as part of Materialized View)

### WindowedCount

Counts records per key within a tumbling time window.

| Field | Type | Description |
|-------|------|-------------|
| `windowSizeSeconds` | integer | Size of the tumbling window in seconds |

**Schema behavior**: Pass-through.

**Supported in**: Config-as-code, GUI (as part of Materialized View)

### ReduceLatest

Keeps only the latest value per key. Useful for building lookup tables or materialized views from changelog streams.

**Schema behavior**: Pass-through.

**Supported in**: Config-as-code, GUI (as part of Materialized View)

## Enrichment Nodes

Enrichment nodes add new fields to each record by calling external services or applying models.

### GeoIp

Resolves an IP address field to a geographic location using the bundled MaxMind GeoLite2 database.

| Field | Type | Description |
|-------|------|-------------|
| `ipField` | string | Name of the field containing the IP address |
| `outputField` | string | Name of the new field to add with the geo result |

**Schema behavior**: Adds `outputField` (type: string) to the schema. Validates that `ipField` exists in the input schema at compile time.

**Supported in**: CLI DSL (`enrich`), config-as-code, GUI

### TextExtractor

Extracts text content from a file using Apache Tika.

| Field | Type | Description |
|-------|------|-------------|
| `filePathField` | string | Name of the field containing the file path or URL |
| `outputField` | string | Name of the new field to add with the extracted text |

**Schema behavior**: Adds `outputField` (type: string) to the schema. Validates that `filePathField` exists.

**Supported in**: Config-as-code, GUI

### EmbeddingGenerator

Generates vector embeddings from a text field using an embedding model.

| Field | Type | Description |
|-------|------|-------------|
| `textField` | string | Name of the field containing the source text |
| `outputField` | string | Name of the new field for the embedding vector |
| `model` | string | Embedding model name |

**Schema behavior**: Adds `outputField` (type: list of floats) to the schema. Validates that `textField` exists.

**Supported in**: Config-as-code, GUI

### OpenAiTransformer

Applies an OpenAI LLM prompt to each record.

| Field | Type | Description |
|-------|------|-------------|
| `prompt` | string | The prompt template (can reference record fields) |
| `outputField` | string | Name of the new field for the LLM response |
| `model` | string | OpenAI model name |

**Schema behavior**: Adds `outputField` (type: string) to the schema.

**Supported in**: Config-as-code, GUI

## Sink / Inspection Nodes

Sink nodes write pipeline output to a destination. Inspector nodes tap the stream without altering data flow.

### Sink

Writes records to a Kafka topic. Can also trigger Kafka Connect connectors for database, Weaviate, or Elasticsearch sinks.

| Field | Type | Description |
|-------|------|-------------|
| `output` | path | Destination topic path |
| `encoding` | `AVRO` \| `JSON` | Output encoding (defaults to source encoding) |

**Schema behavior**: Copies input schema to the output topic.

**Supported in**: CLI DSL (`>`), config-as-code, GUI (Kafka Sink, DB Sink, Weaviate Sink, Elasticsearch Sink)

### Inspector

Taps the data stream for live preview and debugging. Does not modify or persist data.

| Field | Type | Description |
|-------|------|-------------|
| `label` | string | A label for the inspection point |

**Schema behavior**: Pass-through.

**Supported in**: GUI
