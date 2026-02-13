# TypeStream Server Architecture

> Part of [TypeStream Architecture](../ARCHITECTURE.md)
> See [Node Reference](../ARCHITECTURE.md#node-reference) and [Pipeline Lifecycle](../ARCHITECTURE.md#pipeline-lifecycle) for system-wide concepts.

## Overview

The server is the core of TypeStream -- a Kotlin-based streaming data platform that compiles pipe-based commands into Kafka Streams topologies. It provides a UNIX-like abstraction over Kafka topics (`/dev/kafka/...`) and executes streaming jobs.

## Tech Stack

| Component | Technology |
|-----------|------------|
| Language | Kotlin |
| Build | Gradle |
| API | gRPC with Protocol Buffers |
| Streaming | Apache Kafka Streams |
| Schemas | Avro, Protobuf (via Schema Registry) |
| Async | Kotlin Coroutines |

## Directory Structure

```
server/src/main/kotlin/io/typestream/
├── Main.kt, Server.kt, Worker.kt    # Entry points
├── compiler/                        # Compilation pipeline
│   ├── Compiler.kt                  # Text → Graph
│   ├── GraphCompiler.kt             # Proto → Graph
│   ├── Interpreter.kt               # AST type binding
│   ├── Infer.kt                     # Type validation
│   ├── PipelineGraphEmitter.kt      # DSL → PipelineGraph proto
│   ├── ast/                         # AST node definitions
│   ├── lexer/                       # Tokenization
│   ├── parser/                      # Parsing
│   ├── node/                        # Execution graph nodes
│   ├── types/                       # Type system (DataStream, Schema)
│   └── vm/                          # Virtual machine
├── pipeline/                        # Pipeline-as-Code
│   ├── PipelineFileParser.kt        # YAML pipeline file parsing
│   └── PipelineStateStore.kt        # Kafka-backed persistent state
├── scheduler/                       # Job lifecycle
│   ├── Scheduler.kt                 # Job orchestration
│   ├── KafkaStreamsJob.kt           # Embedded execution
│   └── K8sJob.kt                    # Kubernetes execution
├── filesystem/                      # Virtual filesystem
├── server/                          # gRPC service implementations
│   ├── PipelineService.kt          # Pipeline CRUD + plan/diff
│   ├── JobService.kt               # Job management
│   ├── ConnectionService.kt        # Database connections + JDBC sink
│   ├── FileSystemService.kt        # Virtual filesystem operations
│   ├── InteractiveSessionService.kt # Interactive REPL
│   └── StateQueryService.kt        # State store queries
├── kafka/                           # Kafka utilities & serdes
├── geoip/                           # GeoIP enrichment service
├── textextractor/                   # Text extraction service
├── embedding/                       # Embedding generation service
└── openai/                          # OpenAI transformer service
```

## Compilation Pipeline

The server transforms user programs into typed execution graphs. For the full list of node types and their behaviors, see the [Node Reference](../ARCHITECTURE.md#node-reference) in the root architecture doc.

### Two Compilation Paths

1. **Text-based** (`Compiler.kt`): Parses TypeStream DSL commands through Lexer -> Parser -> Interpreter -> Graph<Node>
2. **Proto-based** (`GraphCompiler.kt`): Accepts UI-built `PipelineGraph` protos and compiles them to `Graph<Node>`

Both paths converge at `Graph<Node>`, which is then passed to `Infer` for type validation and wrapped in a `Program`.

```
Text Path:  Source Code → [Lexer] → [Parser] → [Interpreter] → Graph<Node>
Proto Path: PipelineGraph proto → [GraphCompiler] → Graph<Node>
                                                        ↓
                                                    [Infer] → Validated Graph
                                                        ↓
                                                    Program (graph + metadata)
```

### GraphCompiler Internals

`GraphCompiler.compile()` translates each `PipelineNode` proto into its corresponding `Node` sealed class instance:

- Resolves `DataStream` paths against the virtual filesystem to bind schema metadata
- Auto-detects encoding from Schema Registry when not explicitly set
- Validates that edges form a valid DAG
- Calls `inferOutputSchema()` on each node to propagate schemas through the graph

### Schema Inference

Each `Node` type encapsulates its own schema transformation via `inferOutputSchema()`:
- Schema inference is co-located with node definitions in `Node.kt`
- Adding a new node type only requires implementing `inferOutputSchema()` in one place
- The `InferenceContext` interface provides access to the filesystem catalog for external lookups
- Validates schema compatibility at compile time

## Jobs

Jobs execute compiled programs as Kafka Streams topologies.

### KafkaStreamsJob

The primary job executor for local/embedded mode:

1. Takes compiled `Program` and `KafkaConfig`
2. `buildTopology()` walks the `Graph<Node>` and converts each node to Kafka Streams operators:
   - `StreamSource` → `StreamsBuilder.stream()` with appropriate serde
   - `Filter` → `.filter()` / `.filterNot()` based on predicate
   - `Group` → `.groupBy()` with key mapper
   - `Count` / `WindowedCount` → `.count()` / `.windowedBy().count()`
   - `Join` → `.join()` / `.leftJoin()` with the secondary stream
   - `GeoIp`, `TextExtractor`, `EmbeddingGenerator`, `OpenAiTransformer` → `.mapValues()` with enrichment logic
   - `Sink` → `.to()` target topic
3. `start()` launches `KafkaStreams` instance
4. `output()` streams results from the job's `-stdout` topic
5. `state()` maps `KafkaStreams.State` to `Job.State`

### Job States

```
STARTING → RUNNING → STOPPING → STOPPED
              ↓
           FAILED
```

### Scheduler

Manages job lifecycle with Kotlin coroutines:
- Channel-based job queue with `submit()` / `kill()` / `ps()`
- Thread-safe job collection via `ConcurrentHashMap`
- Supports K8s mode for external job monitoring via `K8sJob`

## Pipeline-as-Code

The `PipelineService` provides declarative pipeline management with persistent state.

### PipelineService

Manages the full lifecycle of named pipelines:

| RPC | Purpose |
|-----|---------|
| `ValidatePipeline` | Dry-run compile to check for errors |
| `ApplyPipeline` | Create or update a pipeline (stop old job, start new) |
| `DeletePipeline` | Stop and remove a pipeline |
| `ListPipelines` | List all managed pipelines with job state |
| `PlanPipelines` | Dry-run diff showing CREATE/UPDATE/DELETE/NO_CHANGE actions |

Apply logic:
1. If a pipeline with the same name exists and the graph is unchanged, returns `UNCHANGED`
2. If the graph changed, kills the existing job, compiles and starts a new one, returns `UPDATED`
3. For new pipelines, compiles and starts, returns `CREATED`
4. Persists the pipeline record to the state store after successful apply

### PipelineStateStore

Persistent pipeline state backed by a Kafka compacted topic:

- **Topic**: `__typestream_pipelines` (compacted, single partition)
- **Key**: pipeline name (string)
- **Value**: JSON-serialized `PipelineRecord` containing `PipelineMetadata` + `PipelineGraph` proto + `appliedAt` timestamp
- **Tombstones**: `null` value for deletions

On server startup, `PipelineStateStore.load()` consumes the entire compacted topic from beginning to end, rebuilding the current state map. Then `PipelineService.recoverPipelines()` recompiles and restarts each pipeline's Kafka Streams job.

## gRPC Services

| Service | Methods | Purpose |
|---------|---------|---------|
| PipelineService | ValidatePipeline, ApplyPipeline, DeletePipeline, ListPipelines, PlanPipelines | Declarative pipeline management |
| JobService | CreateJob, CreateJobFromGraph, ListJobs, InferGraphSchemas | Job management and schema inference |
| FileSystemService | Mount, Unmount, Ls | Virtual filesystem operations |
| InteractiveSessionService | StartSession, RunProgram, GetProgramOutput | Interactive REPL |
| ConnectionService | RegisterConnection, GetConnectionStatuses, TestConnection, CreateJdbcSinkConnector | Database connection monitoring + sink connector management |
| StateQueryService | GetValue, GetAllValues, ListStores | Interactive queries on running Kafka Streams state stores |

All services are registered in `Server.kt` with `ExceptionInterceptor` and `LoggerInterceptor`. Proto reflection is enabled for tools like `grpcurl`.

## Virtual Filesystem

Kafka topics are exposed as a UNIX-like filesystem:

```
/dev/kafka/{cluster}/topics/{topic}
```

- **FileSystem.kt**: Root filesystem abstraction, watches for metadata changes
- **Catalog**: Metadata cache backed by Schema Registry lookups
- Enables commands like `cat`, `ls`, `grep` on topics

## Avro Serialization

TypeStream uses Avro as its primary serialization format. The `kafka/` package handles serialization/deserialization with Schema Registry integration.

The deserializer fetches the **writer's schema** from Schema Registry using the schema ID embedded in each message (`[0x00][schema ID (4 bytes)][avro binary]`). This enables TypeStream to read topics produced by external systems (like Debezium CDC) that use different schema namespaces.

### Key Files

| File | Purpose |
|------|---------|
| `AvroSerde.kt` | Kafka serde with schema registry lookup |
| `SchemaRegistryClient.kt` | HTTP client for Schema Registry API |
| `GenericDataWithLogicalTypes.kt` | Avro logical type support |

## Debezium Source (CDC)

TypeStream supports consuming Change Data Capture (CDC) events from Debezium. The `unwrapCdc` flag on `StreamSource` nodes extracts the "after" payload from CDC envelope structures.

```
Debezium Topic (CDC envelope: before/after/source/op/ts_ms)
        ↓
StreamSource(unwrapCdc=true)
        ↓
DataStream.unwrapCdc() extracts schema from "after" field
        ↓
Downstream nodes receive flattened records
```

## Kafka Connect Sink (JDBC)

TypeStream creates JDBC sink connectors via Kafka Connect to write processed data to databases.

### Security Model

Credentials never leave the server:

```
Client → Server: connectionId (e.g., "dev-postgres")
Server: Looks up full DatabaseConnectionConfig from connections map
Server → Kafka Connect: Full JDBC URL with credentials
```

### Data Flow

```
Kafka Streams Job                    Kafka Connect
[StreamSource] → [Transform] → [Sink]     [JdbcSinkConnector]
                                ↓              ↓
                        intermediate_topic ──→ Database Table
```

If connector creation fails, `JobService` performs rollback by killing the job and deleting any created connectors.

## Integration Points

### Kafka (Redpanda)
- **Streams**: `KafkaStreamsJob` creates `KafkaStreams` instances with `StreamsBuilder` topology
- **Admin**: `PipelineStateStore` creates and manages the `__typestream_pipelines` compacted topic
- **Producer/Consumer**: `PipelineStateStore` uses raw Kafka producer/consumer for state persistence

### Schema Registry
- **Read path**: `SchemaRegistryClient` fetches writer schemas by ID for deserialization
- **Write path**: `AvroSerde` registers schemas when producing to new topics
- **Catalog sync**: `FileSystem` watches Schema Registry for metadata changes

### Kafka Connect
- **REST API**: `ConnectionService.createKafkaConnectConnector()` POSTs connector configs
- **Connector types**: JDBC Sink (`io.debezium.connector.jdbc.JdbcSinkConnector`), Weaviate Sink, Elasticsearch Sink

### gRPC Clients (UI, CLI)
- Server listens on configurable port (default 8080)
- Connect Protocol (gRPC-Web) for browser clients via Envoy proxy
- Native gRPC for CLI clients

### External Services
- **GeoIP**: MaxMind GeoLite2 database for IP geolocation enrichment
- **OpenAI**: REST API for embedding generation and LLM transformations

## Integration Tests

Integration tests use **Testcontainers with real Kafka** (Redpanda) to verify pipelines end-to-end.

### Test Infrastructure

- `TestKafkaContainer.instance` singleton provides a shared Redpanda container
- `TestKafka.uniqueTopic("name")` generates isolated topic names per test
- `testKafka.produceRecords(topic, encoding, records...)` for test data
- gRPC services tested via `InProcessServerBuilder` + `GrpcCleanupRule`

### Key Test Files

| File | Purpose |
|------|---------|
| `GraphCompilerTest.kt` | Graph compilation, schema propagation through node chains |
| `PreviewJobIntegrationTest.kt` | End-to-end message flow via gRPC streaming |
| `JobServiceTest.kt` | Graph-to-job creation via gRPC |
| `PipelineServiceTest.kt` | Pipeline CRUD, plan/diff, state persistence |
| `PipelineStateStoreTest.kt` | State store load/save/delete with real Kafka |
| `StateQueryServiceTest.kt` | State stores and interactive queries on running pipelines |

### Adding Tests for New Nodes

When adding a new node type, add integration tests that verify:

1. **Schema propagation**: Test that `inferNodeSchemasForUI()` correctly propagates schemas through your node
   - See `GraphCompilerTest.inferNodeSchemasForUI propagates schemas through chain`
2. **Graph compilation**: Test that `GraphCompiler.compile()` handles your node in a pipeline
   - Build a graph with `StreamSource -> YourNode -> Sink` and verify it compiles
3. **Message flow** (if the node transforms data): Test in `PreviewJobIntegrationTest` that messages flow through correctly

### Example Test Pattern

```kotlin
@Test
fun `compiles stream source with your node`() {
    val graph = pipelineGraph {
        nodes {
            streamSource("source", dataStream, Encoding.AVRO)
            yourNode("node", /* config */)
            sink("sink", sinkDataStream)
        }
        edges {
            edge("source", "node")
            edge("node", "sink")
        }
    }

    val program = GraphCompiler(fileSystem).compile(graph)
    assertThat(program.isRight()).isTrue()
}
```

## Key Files

| File | Purpose |
|------|---------|
| `Compiler.kt` | Text -> Graph compilation |
| `GraphCompiler.kt` | Proto -> Graph compilation |
| `PipelineGraphEmitter.kt` | DSL -> PipelineGraph proto conversion |
| `Interpreter.kt` | AST traversal, type binding |
| `Node.kt` | Node types with schema inference |
| `KafkaStreamsJob.kt` | Topology builder, job execution |
| `Scheduler.kt` | Job queue and lifecycle |
| `Vm.kt` | Execution routing (KAFKA vs SHELL) |
| `PipelineService.kt` | Pipeline CRUD, plan/diff, recovery |
| `PipelineStateStore.kt` | Kafka compacted topic state persistence |
| `ConnectionService.kt` | JDBC connection monitoring and sink connector management |
| `DataStreamSerde.kt` | Kafka serialization |
