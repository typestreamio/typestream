> Part of [TypeStream Architecture](../ARCHITECTURE.md)

# Protocol Buffers -- API Contracts

The `protos/` module defines the gRPC service contracts and data models that form the API surface of the TypeStream platform. These proto definitions are the single source of truth consumed by three codegen targets: Kotlin (server), Go (CLI), and TypeScript (UI).

## Proto File Catalog

| File | Package | Services | Key Messages |
|------|---------|----------|--------------|
| `connection.proto` | `io.typestream.grpc` | `ConnectionService` | `DatabaseConnectionConfig`, `WeaviateConnectionConfig`, `ElasticsearchConnectionConfig`, `ConnectionStatus` |
| `filesystem.proto` | `io.typestream.grpc` | `FileSystemService` | `FileInfo`, `LsResponse`, `GetSchemaResponse` |
| `interactive_session.proto` | `io.typestream.grpc` | `InteractiveSessionService` | `RunProgramResponse`, `GetProgramOutputResponse`, `CompleteProgramResponse` |
| `job.proto` | `io.typestream.grpc` | `JobService` | `PipelineGraph`, `PipelineNode`, `JobInfo`, `CreatePreviewJobRequest`, `NodeSchemaResult` |
| `pipeline.proto` | `io.typestream.grpc` | `PipelineService` | `PipelineMetadata`, `PipelineInfo`, `PipelinePlanResult` |
| `state_query.proto` | `io.typestream.grpc` | `StateQueryService` | `StoreInfo`, `KeyValuePair`, `GetValueResponse` |

All files use `syntax = "proto3"` and share the `io.typestream.grpc` package. Each file sets language-specific package options (`java_package`, `go_package`).

## Service Reference

### ConnectionService (`connection.proto`)

Manages external data store connections (databases, Weaviate, Elasticsearch) and creates Kafka Connect sink connectors.

| RPC | Request | Response | Description |
|-----|---------|----------|-------------|
| `RegisterConnection` | `RegisterConnectionRequest` | `RegisterConnectionResponse` | Register a database connection for health monitoring |
| `UnregisterConnection` | `UnregisterConnectionRequest` | `UnregisterConnectionResponse` | Stop monitoring a connection |
| `GetConnectionStatuses` | `GetConnectionStatusesRequest` | `GetConnectionStatusesResponse` | Get status of all monitored database connections |
| `TestConnection` | `TestConnectionRequest` | `TestConnectionResponse` | One-shot connection test (no registration) |
| `CreateJdbcSinkConnector` | `CreateJdbcSinkConnectorRequest` | `CreateJdbcSinkConnectorResponse` | Create a JDBC sink connector (credentials resolved server-side) |
| `RegisterWeaviateConnection` | `RegisterWeaviateConnectionRequest` | `RegisterWeaviateConnectionResponse` | Register a Weaviate connection |
| `GetWeaviateConnectionStatuses` | `GetWeaviateConnectionStatusesRequest` | `GetWeaviateConnectionStatusesResponse` | Get status of all Weaviate connections |
| `CreateWeaviateSinkConnector` | `CreateWeaviateSinkConnectorRequest` | `CreateWeaviateSinkConnectorResponse` | Create a Weaviate sink connector |
| `RegisterElasticsearchConnection` | `RegisterElasticsearchConnectionRequest` | `RegisterElasticsearchConnectionResponse` | Register an Elasticsearch connection |
| `GetElasticsearchConnectionStatuses` | `GetElasticsearchConnectionStatusesRequest` | `GetElasticsearchConnectionStatusesResponse` | Get status of all Elasticsearch connections |
| `CreateElasticsearchSinkConnector` | `CreateElasticsearchSinkConnectorRequest` | `CreateElasticsearchSinkConnectorResponse` | Create an Elasticsearch sink connector |

Security note: Connection configs include credentials (`DatabaseConnectionConfig.password`, `WeaviateConnectionConfig.api_key`, `ElasticsearchConnectionConfig.password`), but public-facing status messages use credential-stripped variants (`DatabaseConnectionConfigPublic`, `WeaviateConnectionConfigPublic`, `ElasticsearchConnectionConfigPublic`).

### FileSystemService (`filesystem.proto`)

Virtual filesystem that exposes Kafka topics as paths.

| RPC | Request | Response | Description |
|-----|---------|----------|-------------|
| `Mount` | `MountRequest` | `MountResponse` | Mount a data source endpoint |
| `Unmount` | `UnmountRequest` | `UnmountResponse` | Unmount an endpoint |
| `Ls` | `LsRequest` | `LsResponse` | List files/topics at a path |
| `GetSchema` | `GetSchemaRequest` | `GetSchemaResponse` | Get field names for a topic |

### InteractiveSessionService (`interactive_session.proto`)

REPL-style interactive shell for running TypeStream DSL programs.

| RPC | Request | Response | Description |
|-----|---------|----------|-------------|
| `StartSession` | `StartSessionRequest` | `StartSessionResponse` | Create a new interactive session |
| `RunProgram` | `RunProgramRequest` | `RunProgramResponse` | Execute DSL source in a session |
| `GetProgramOutput` | `GetProgramOutputRequest` | `stream GetProgramOutputResponse` | Stream ongoing output from a running program |
| `CompleteProgram` | `CompleteProgramRequest` | `CompleteProgramResponse` | Tab-completion for DSL source |
| `StopSession` | `StopSessionRequest` | `StopSessionResponse` | Terminate a session |

### JobService (`job.proto`)

Core service for creating and managing Kafka Streams jobs.

| RPC | Request | Response | Description |
|-----|---------|----------|-------------|
| `CreateJob` | `CreateJobRequest` | `CreateJobResponse` | Create a job from DSL source code |
| `CreateJobFromGraph` | `CreateJobFromGraphRequest` | `CreateJobResponse` | Create a job from a `PipelineGraph` (used by the UI) |
| `ListJobs` | `ListJobsRequest` | `ListJobsResponse` | List all jobs with state and throughput metrics |
| `CreatePreviewJob` | `CreatePreviewJobRequest` | `CreatePreviewJobResponse` | Start a temporary preview job for data inspection |
| `StopPreviewJob` | `StopPreviewJobRequest` | `StopPreviewJobResponse` | Stop a preview job |
| `StreamPreview` | `StreamPreviewRequest` | `stream StreamPreviewResponse` | Stream key-value records from a preview job |
| `InferGraphSchemas` | `InferGraphSchemasRequest` | `InferGraphSchemasResponse` | Infer output schemas for all nodes in a graph |
| `ListOpenAIModels` | `ListOpenAIModelsRequest` | `ListOpenAIModelsResponse` | List available OpenAI models for AI nodes |

### PipelineService (`pipeline.proto`)

Pipeline-as-code management: validate, apply, plan, and delete named pipelines.

| RPC | Request | Response | Description |
|-----|---------|----------|-------------|
| `ValidatePipeline` | `ValidatePipelineRequest` | `ValidatePipelineResponse` | Validate a pipeline definition without applying |
| `ApplyPipeline` | `ApplyPipelineRequest` | `ApplyPipelineResponse` | Create or update a named pipeline |
| `ListPipelines` | `ListPipelinesRequest` | `ListPipelinesResponse` | List all applied pipelines with job state |
| `DeletePipeline` | `DeletePipelineRequest` | `DeletePipelineResponse` | Delete a named pipeline |
| `PlanPipelines` | `PlanPipelinesRequest` | `PlanPipelinesResponse` | Dry-run: show what would change (CREATE/UPDATE/DELETE/NO_CHANGE) |

### StateQueryService (`state_query.proto`)

Interactive queries against KTable state stores from running Kafka Streams jobs.

| RPC | Request | Response | Description |
|-----|---------|----------|-------------|
| `ListStores` | `ListStoresRequest` | `ListStoresResponse` | List queryable state stores from running jobs |
| `GetAllValues` | `GetAllValuesRequest` | `stream KeyValuePair` | Stream all key-value pairs from a store (with limit) |
| `GetValue` | `GetValueRequest` | `GetValueResponse` | Look up a single value by string key |

Limitation: `GetValue` only supports simple string keys. For complex keys (structs), use `GetAllValues` and filter client-side.

## PipelineGraph Data Model

`PipelineGraph` is the canonical intermediate representation for visual pipelines. It is a directed acyclic graph of typed nodes connected by edges.

```protobuf
message PipelineGraph {
  repeated PipelineNode nodes = 1;
  repeated PipelineEdge edges = 2;
}

message PipelineEdge {
  string from_id = 1;
  string to_id = 2;
}
```

### PipelineNode

Each node has a unique `id` and exactly one `node_type` via a `oneof` discriminator. There are **17 node types**:

#### Source Nodes
| Node | Fields | Description |
|------|--------|-------------|
| `StreamSourceNode` | `data_stream`, `encoding`, `unwrap_cdc` | Reads from a Kafka topic. `unwrap_cdc` extracts the `after` payload from CDC envelopes. |
| `ShellSourceNode` | `data` (repeated) | Source for interactive shell commands, referencing multiple data streams. |

#### Transform Nodes
| Node | Fields | Description |
|------|--------|-------------|
| `FilterNode` | `by_key`, `predicate` | Filters records. `predicate.expr` holds the filter expression; `by_key` applies it to keys. |
| `MapNode` | `mapper_expr` | Transforms record values using a mapping expression. |
| `EachNode` | `fn_expr` | Applies a side-effect function to each record. |
| `GroupNode` | `key_mapper_expr` | Re-keys records for downstream aggregation. |
| `JoinNode` | `with`, `join_type` | Joins with another stream. `join_type` controls `by_key` and `is_lookup` semantics. |
| `NoOpNode` | _(none)_ | Pass-through, used as a structural placeholder. |

#### Aggregation Nodes
| Node | Fields | Description |
|------|--------|-------------|
| `CountNode` | _(none)_ | Counts records into a KTable. |
| `WindowedCountNode` | `window_size_seconds` | Time-windowed count (e.g., 60 for 1-minute tumbling windows). |
| `ReduceLatestNode` | _(none)_ | Keeps only the latest value per key. |

#### AI / Enrichment Nodes
| Node | Fields | Description |
|------|--------|-------------|
| `GeoIpNode` | `ip_field`, `output_field` | Enriches records with geolocation data from an IP address field. |
| `TextExtractorNode` | `file_path_field`, `output_field` | Extracts text content from a file path field. |
| `EmbeddingGeneratorNode` | `text_field`, `output_field`, `model` | Generates vector embeddings via OpenAI. |
| `OpenAiTransformerNode` | `prompt`, `output_field`, `model` | Transforms records using an OpenAI LLM with a user prompt. |

#### Sink / Inspection Nodes
| Node | Fields | Description |
|------|--------|-------------|
| `SinkNode` | `output`, `encoding` | Writes records to an output Kafka topic. |
| `InspectorNode` | `label` | Taps the stream for preview/debugging without altering data flow. |

### Sink Connector Configs

`CreateJobFromGraphRequest` carries optional sink connector configurations alongside the graph:

- **`DbSinkConfig`** -- JDBC sink: `connection_id`, `table_name`, `insert_mode`, `primary_key_fields`, `intermediate_topic`
- **`WeaviateSinkConfig`** -- Weaviate vector DB sink: `collection_name`, `document_id_strategy`, `vector_strategy`, `timestamp_field`
- **`ElasticsearchSinkConfig`** -- Elasticsearch sink: `index_name`, `document_id_strategy`, `write_method`, `behavior_on_null_values`

All sink configs use `connection_id` for server-side credential resolution -- credentials never leave the server.

### Supporting Types

- **`Encoding`** enum: `STRING`, `NUMBER`, `JSON`, `AVRO`, `PROTOBUF`
- **`JobState`** enum: `STARTING`, `RUNNING`, `STOPPING`, `STOPPED`, `FAILED`, `UNKNOWN`
- **`PipelineState`** enum: `CREATED`, `UPDATED`, `UNCHANGED`
- **`PipelineAction`** enum: `CREATE`, `UPDATE`, `NO_CHANGE`, `DELETE`
- **`ConnectionState`** enum: `CONNECTED`, `DISCONNECTED`, `ERROR`, `CONNECTING`
- **`DatabaseType`** enum: `POSTGRES`, `MYSQL`

## Code Generation

### Kotlin (Server)

The `stub/` module uses the [protobuf-gradle-plugin](https://github.com/google/protobuf-gradle-plugin) v0.9.4:

```
stub/build.gradle.kts
  protobuf(project(":protos"))     <- imports proto sources
  protoc-gen-grpc-java             <- Java gRPC stubs
  protoc-gen-grpc-kotlin           <- Kotlin coroutine stubs
  builtin kotlin                   <- Kotlin DSL builders
```

Generated code lands in `stub/build/generated/source/proto/main/{java,grpc,kotlin,grpckt}`. Build with:

```bash
./gradlew :stub:generateProto
```

### Go (CLI)

The `cli/Makefile` runs `protoc` directly for each service:

```bash
cd cli && make proto
```

Each service generates into its own package under `cli/pkg/<service>/`:
- `interactive_session_service/`
- `job_service/`
- `filesystem_service/`
- `pipeline_service/`

Version constraints: `protoc-gen-go v1.31.0` and `protoc-gen-go-grpc v1.2.0`. Newer versions produce `SupportPackageIsVersion9` which is incompatible with the project's `grpc v1.59.0`.

Note: `pipeline.proto` imports `job.proto` (for `PipelineGraph`, `JobState`). The Makefile generates them separately to avoid duplicate type definitions.

### TypeScript (UI)

The `uiv2/` directory uses [buf](https://buf.build/) with `buf.yaml` pointing at `../protos/src/main/proto`:

```yaml
# buf.gen.yaml
plugins:
  - protoc-gen-es          -> TypeScript message types
  - protoc-gen-connect-es  -> Connect-ES client stubs
  - protoc-gen-connect-query -> TanStack Query integration
```

Generated code goes to `uiv2/src/generated/`. The UI uses `@bufbuild/protobuf` for `toJson()`/`fromJson()` serialization.

## Package Naming

All proto files use `package io.typestream.grpc` with per-language overrides:

| Language | Package Pattern | Example |
|----------|----------------|---------|
| Java/Kotlin | `io.typestream.grpc.<service>_service` | `io.typestream.grpc.job_service` |
| Go | `github.com/typestreamio/typestream/cli/pkg/<service>_service` | `.../pkg/job_service` |
| TypeScript | Generated by buf into `src/generated/` | flat output directory |

## Integration Points

```
protos/src/main/proto/*.proto
        |
        +---> stub/ (Gradle)  ---> server/  (Kotlin gRPC services)
        |
        +---> cli/  (protoc)  ---> Go CLI   (Cobra commands calling gRPC)
        |
        +---> uiv2/ (buf)     ---> React UI (Connect-ES + TanStack Query)
```

- **Server** implements all six gRPC services, using the generated Kotlin coroutine stubs from `stub/`.
- **CLI** calls `InteractiveSessionService`, `JobService`, `FileSystemService`, and `PipelineService` through Go gRPC stubs.
- **UI** calls `JobService`, `ConnectionService`, `FileSystemService`, `PipelineService`, and `StateQueryService` through Connect-ES (gRPC-Web).
- **`PipelineGraph`** is the shared data model: the UI builds it visually, sends it via `CreateJobFromGraph` or `ApplyPipeline`, and the server compiles it to a Kafka Streams topology.
