# TypeStream Architecture

TypeStream is a streaming data platform that compiles visual or text-based pipeline definitions into Kafka Streams topologies. It provides a UNIX-like abstraction over Kafka topics, a visual graph builder, and declarative pipeline-as-code management.

## System Architecture

```
                           ┌──────────────────┐
                           │    React UI       │ :80 (Caddy)
                           │  (Graph Builder)  │
                           └────────┬─────────┘
                                    │ Connect Protocol (gRPC-Web)
                           ┌────────┴─────────┐
                           │     Envoy         │ :8080
                           │  (gRPC-Web Proxy) │
                           └────────┬─────────┘
              ┌─────────────────────┼──────────────────────┐
              │ gRPC :4242          │                       │
     ┌────────┴─────────┐ ┌────────┴─────────┐   ┌────────┴─────────┐
     │    Go CLI         │ │  TypeStream      │   │  Kafka Connect   │ :8083
     │  (Cobra + gRPC)   │ │  Server (Kotlin) │   │  (Debezium +     │
     └──────────────────┘ │                   │   │   JDBC/Weaviate)  │
                          └────────┬─────────┘   └────────┬─────────┘
                                   │                       │
              ┌────────────────────┼───────────────────────┤
              │                    │                        │
     ┌────────┴─────────┐ ┌───────┴──────────┐  ┌────────┴─────────┐
     │    Redpanda       │ │ Schema Registry   │  │   PostgreSQL     │
     │  (Kafka broker)   │ │ (built into RP)   │  │  (CDC source)    │
     │  :19092           │ │ :18081            │  │  :5432            │
     └──────────────────┘ └──────────────────┘  └──────────────────┘
```

**Data path**: Sources (Kafka topics, CDC) → Server compiles pipeline → Kafka Streams topology → Sinks (Kafka topics, JDBC, Weaviate, Elasticsearch)

**Control path**: UI/CLI → gRPC → Server → compiles, schedules, and monitors jobs

## Component Overview

| Directory | Purpose | Tech | Docs |
|-----------|---------|------|------|
| `server/` | Core platform: compiler, scheduler, gRPC services, Kafka Streams execution | Kotlin, Gradle, gRPC, Kafka Streams | [server/ARCHITECTURE.md](server/ARCHITECTURE.md) |
| `uiv2/` | Visual pipeline builder and job dashboard | React 19, TypeScript, XYFlow, MUI | [uiv2/ARCHITECTURE.md](uiv2/ARCHITECTURE.md) |
| `cli/` | Local dev environment, pipeline-as-code, interactive shell | Go, Cobra, gRPC, Docker Compose | [cli/ARCHITECTURE.md](cli/ARCHITECTURE.md) |
| `protos/` | gRPC service contracts and data models (6 services) | Protocol Buffers | [protos/ARCHITECTURE.md](protos/ARCHITECTURE.md) |
| `connectors/demo-data/` | Real-time data generators (Coinbase, Wikipedia, web visits, file uploads) | Kotlin, Avro, WebSocket/SSE | [connectors/demo-data/ARCHITECTURE.md](connectors/demo-data/ARCHITECTURE.md) |
| `stub/` | Generated Kotlin/Java gRPC stubs from protos | Gradle protobuf plugin | -- |
| `libs/testing/` | Shared test infrastructure (TestKafkaContainer, TestKafka helpers) | Kotlin, Testcontainers, Redpanda | -- |
| `libs/k8s-client/` | Kubernetes client wrapper for K8s job execution | Kotlin, fabric8 | -- |
| `libs/option/` | Functional option type | Kotlin | -- |
| `config/` | Shared configuration (TOML parsing, KafkaConfig, GrpcConfig) | Kotlin | -- |
| `tools/` | Development utilities | Kotlin | -- |
| `buildSrc/` | Gradle convention plugins (`typestream.kotlin-conventions`) | Kotlin DSL | -- |
| `docker/` | Envoy config, Caddyfile, Kafka Connect Dockerfile, postgres-init.sql | Docker, YAML | -- |
| `scripts/` | Dev scripts (`dev/server.sh`, `dev/seed.sh`), release, deploy | Bash | -- |

## Pipeline Lifecycle

A pipeline goes from definition to execution through four possible entry points, all converging on a shared compilation and execution path.

### Entry Points

```
  ┌─────────────────┐   ┌─────────────────┐   ┌─────────────────┐   ┌─────────────────┐
  │  GUI             │   │  CLI Config      │   │  CLI Run         │   │  Interactive     │
  │  (Graph Builder) │   │  (apply/plan)    │   │  (run command)   │   │  Shell (REPL)    │
  └────────┬────────┘   └────────┬────────┘   └────────┬────────┘   └────────┬────────┘
           │                      │                      │                      │
  React Flow graph       .typestream.json        DSL source text        DSL source text
           │                      │                      │                      │
  serializeGraphWithSinks()  parsePipelineFile()         │                      │
           │                      │                      │                      │
  PipelineGraph proto    PipelineGraph proto     CreateJobRequest       RunProgramRequest
           │                      │                      │                      │
  CreateJobFromGraph     ApplyPipeline           CreateJob              RunProgram
  (JobService gRPC)      (PipelineService gRPC)  (JobService gRPC)     (InteractiveSession gRPC)
           │                      │                      │                      │
           └──────────┬───────────┘                      └──────────┬───────────┘
                      │                                              │
              GraphCompiler.compile()                        Compiler.compile()
              (proto → Graph<Node>)                  (text → Lexer → Parser → Interpreter
                      │                                      → Graph<Node>)
                      │                                              │
                      └────────────────────┬─────────────────────────┘
                                           │
                                    Graph<Node> (validated)
                                           │
                                      Program (graph + metadata)
                                           │
                                    Scheduler.submit()
                                           │
                                    KafkaStreamsJob.start()
                                           │
                                    Kafka Streams topology running
```

### Compilation

**Proto path** (`GraphCompiler`): Receives a `PipelineGraph` proto (from UI or config files). Each `PipelineNode` is resolved against the virtual filesystem to bind schemas, then converted to the corresponding `Node` sealed class. Schema inference runs through the graph.

**Text path** (`Compiler`): Parses TypeStream DSL through Lexer → Parser → AST → Interpreter. The Interpreter binds types and builds a `Graph<Node>`. When possible, it also emits a `PipelineGraph` proto via `PipelineGraphEmitter` for persistence.

Both paths produce a validated `Graph<Node>` wrapped in a `Program`.

### Execution

The `Scheduler` receives a `Program` and creates a `KafkaStreamsJob`:

1. `buildTopology()` walks the node graph and maps each `Node` to Kafka Streams operators
2. `KafkaStreams` instance is started with the topology
3. Job state is tracked: `STARTING → RUNNING → STOPPING → STOPPED` (or `FAILED`)
4. Output is available via a `-stdout` topic for streaming results

### Persistence (Pipeline-as-Code)

Named pipelines are persisted to a Kafka compacted topic (`__typestream_pipelines`):

- **Key**: pipeline name (string)
- **Value**: JSON-serialized record containing `PipelineMetadata` + `PipelineGraph` proto + `appliedAt` timestamp
- **Tombstones**: `null` value for deletions

On server startup, `PipelineStateStore.load()` consumes the entire topic, and `PipelineService.recoverPipelines()` recompiles and restarts each pipeline.

### Preview

Inspector nodes tap the stream without altering data flow. The UI uses `CreatePreviewJob` → `StreamPreview` (server-sent stream) to show live data in the graph builder.

## Node Reference

The server implements 18 node types as a Kotlin sealed interface (`Node.kt`) with each type in its own file (`NodeFilter.kt`, `NodeMap.kt`, etc.). Each node encapsulates its own schema transformation via `inferOutputSchema()`.

### Source Nodes

| Node | Config | Schema Behavior |
|------|--------|----------------|
| **StreamSource** | `dataStream`, `encoding`, `unwrapCdc` | Looks up schema from Schema Registry via catalog. If `unwrapCdc=true`, extracts the `after` payload from CDC envelope. |
| **ShellSource** | `data` (list of DataStreams) | Uses first data stream's schema. Always JSON encoding. |

### Transform Nodes

| Node | Config | Schema Behavior |
|------|--------|----------------|
| **Filter** | `byKey`, `predicate` | Pass-through (schema unchanged) |
| **Map** | `mapper` expression | Pass-through (TODO: extract field transforms for accurate typing) |
| **Group** | `keyMapper` expression | Pass-through (re-keys records for downstream aggregation) |
| **Join** | `with` stream, `joinType` | Merges left and right schemas into a combined struct |
| **Each** | `fn` expression | Pass-through (side-effect only) |
| **NoOp** | _(none)_ | Pass-through (structural placeholder, root node) |

### Aggregation Nodes

| Node | Config | Schema Behavior |
|------|--------|----------------|
| **Count** | _(none)_ | Pass-through (counts into a KTable) |
| **WindowedCount** | `windowSizeSeconds` | Pass-through (tumbling window count) |
| **ReduceLatest** | _(none)_ | Pass-through (keeps latest value per key) |

### Enrichment Nodes

| Node | Config | Schema Behavior |
|------|--------|----------------|
| **GeoIp** | `ipField`, `outputField` | Adds `outputField` (string) to schema. Validates `ipField` exists. |
| **TextExtractor** | `filePathField`, `outputField` | Adds `outputField` (string) to schema. Validates `filePathField` exists. |
| **EmbeddingGenerator** | `textField`, `outputField`, `model` | Adds `outputField` (list of floats) to schema. Validates `textField` exists. |
| **OpenAiTransformer** | `prompt`, `outputField`, `model` | Adds `outputField` (string) to schema. |

### Sink / Inspection Nodes

| Node | Config | Schema Behavior |
|------|--------|----------------|
| **Sink** | `output` topic, `encoding` | Copies input schema to output path |
| **Inspector** | `label` | Pass-through (taps stream for preview/debugging) |

## Schema Propagation

Schema inference is a compile-time pass that flows output schemas through the pipeline graph. It enables the UI to show field names on every edge and validate node configurations before execution.

**How it works:**

1. Source nodes resolve their schema from Schema Registry (via the virtual filesystem catalog)
2. Each downstream node's `inferOutputSchema()` receives the upstream `DataStream` and `Encoding`
3. Most nodes pass the schema through unchanged (Filter, Count, Inspector, Each, etc.)
4. Schema-modifying nodes transform the schema:
   - **Join**: merges left + right schemas into a combined struct
   - **Enrichment nodes** (GeoIp, TextExtractor, EmbeddingGenerator, OpenAiTransformer): append a new field
   - **Map**: currently pass-through (accurate field tracking is a TODO)
5. Encoding inherits from the source. If not set, defaults to Avro.

The two-phase approach (inference separate from topology building) allows the UI to report schema errors before any Kafka Streams resources are allocated.

## Build System

### Gradle (Server + Kotlin modules)

Multi-module Gradle project with shared convention plugins:

```
settings.gradle.kts
  ├── config/          - Shared configuration classes
  ├── protos/          - Raw .proto files
  ├── stub/            - Generated Kotlin/Java gRPC stubs (protobuf-gradle-plugin)
  ├── libs/testing/    - Test infrastructure (Testcontainers)
  ├── libs/k8s-client/ - Kubernetes client
  ├── libs/option/     - Option type
  ├── server/          - Main server application
  ├── connectors/demo-data/ - Data generators
  └── tools/           - Dev utilities
```

Convention plugins in `buildSrc/` set Kotlin version, JVM target, and common dependencies. Version catalog in `settings.gradle.kts` manages library versions (Kafka 4.1.1, gRPC 1.57.2, Avro 1.11.3, etc.).

Proto code generation: `./gradlew :stub:generateProto` produces Java/Kotlin stubs from `protos/`.

### Go CLI

Built with Go 1.21. `cli/Makefile` handles proto generation and building. Released via GoReleaser to Homebrew tap (`typestreamio/homebrew-tap`).

### UI (Vite + buf)

`uiv2/` uses Vite for dev/build and buf for proto codegen. `buf generate` produces TypeScript message types + Connect-ES client stubs + TanStack Query hooks in `src/generated/`.

### Proto Flow

```
protos/src/main/proto/*.proto
    ├── stub/ (Gradle protobuf plugin) → server/ (Kotlin gRPC services)
    ├── cli/  (protoc via Makefile)    → Go CLI  (Cobra commands)
    └── uiv2/ (buf)                    → React UI (Connect-ES + TanStack Query)
```

## Infrastructure

### Docker Compose Services

The full stack (`docker-compose.yml`) includes:

| Service | Image | Port | Purpose |
|---------|-------|------|---------|
| `redpanda` | Redpanda v24.2.9 | 19092 (Kafka), 18081 (Schema Registry) | Kafka-compatible broker |
| `server` | `ghcr.io/typestreamio/server` | 4242 (gRPC) | TypeStream server |
| `envoy` | Envoy v1.28 | 8080 | gRPC-Web proxy for UI |
| `kafka-connect` | Custom Debezium image | 8083 | CDC source + JDBC/Weaviate/ES sinks |
| `postgres` | PostgreSQL 16 | 5432 | Demo database (WAL level=logical for CDC) |
| `tika` | Apache Tika 2.9.2 | 9998 | Text extraction service |
| `uiv2` | `ghcr.io/typestreamio/ui` | -- | React frontend (served via Caddy) |
| `caddy` | Caddy 2 | 80, 443 | Reverse proxy (UI + gRPC) |

**Dev mode** (`docker-compose.dev.yml` overlay): Server runs on the host for hot reload. Envoy routes to `host.docker.internal:4242` instead of the Docker `server` container.

**Demo mode** (`docker-compose.demo.yml`): Adds demo data generators (Coinbase, Wikipedia, web visits).

### External Services

- **GeoIP**: MaxMind GeoLite2 database (bundled in server image)
- **OpenAI**: REST API for embedding generation and LLM transformations

## CI/CD

GitHub Actions workflows:

| Workflow | Trigger | Purpose |
|----------|---------|---------|
| `server-check.yml` | PR / push | Gradle build + test (server, libs, connectors) |
| `frontend-check.yml` | PR / push | Vite build + lint (uiv2) |
| `cli-lint.yml` | PR / push | Go lint (cli) |
| `connectors-check.yml` | PR / push | Connector build check |
| `docs-check.yml` | PR / push | Documentation site build |
| `publish-images.yml` | Release / tag | Build and push Docker images to GHCR |
| `pr-lint.yml` | PR | PR title and label checks |
| `claude-code-review.yml` | PR | AI-assisted code review |
| `renovate.yml` | Schedule | Dependency updates |

## Component Docs

- [server/ARCHITECTURE.md](server/ARCHITECTURE.md) -- Kotlin server internals (compiler, scheduler, gRPC services, Kafka Streams topology building, pipeline persistence)
- [uiv2/ARCHITECTURE.md](uiv2/ARCHITECTURE.md) -- React UI (graph builder, node type mapping, job dashboard, schema validation)
- [cli/ARCHITECTURE.md](cli/ARCHITECTURE.md) -- Go CLI (local dev orchestration, pipeline-as-code commands, interactive shell, proto generation)
- [protos/ARCHITECTURE.md](protos/ARCHITECTURE.md) -- Protocol Buffer API contracts (6 services, PipelineGraph data model, code generation targets)
- [connectors/demo-data/ARCHITECTURE.md](connectors/demo-data/ARCHITECTURE.md) -- Demo data generators (Coinbase, Wikipedia, web visits, file uploads)
