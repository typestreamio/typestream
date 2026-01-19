# TypeStream Server Architecture

## Overview

The server is the core of TypeStream - a Kotlin-based streaming data platform that compiles pipe-based commands into Kafka Streams topologies. It provides a UNIX-like abstraction over Kafka topics (`/dev/kafka/...`) and executes streaming jobs.

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
│   ├── ast/                         # AST node definitions
│   ├── lexer/                       # Tokenization
│   ├── parser/                      # Parsing
│   ├── node/                        # Execution graph nodes
│   ├── types/                       # Type system (DataStream, Schema)
│   └── vm/                          # Virtual machine
├── scheduler/                       # Job lifecycle
│   ├── Scheduler.kt                 # Job orchestration
│   ├── KafkaStreamsJob.kt           # Embedded execution
│   └── K8sJob.kt                    # Kubernetes execution
├── filesystem/                      # Virtual filesystem
├── server/                          # gRPC service implementations
└── kafka/                           # Kafka utilities & serdes
```

## Node Graph Builder

The node graph builder transforms user programs into typed execution graphs through multiple phases:

### Compilation Flow

```
Source Code (e.g., "cat /dev/kafka/.../topic | grep x")
    ↓
[Lexer] → Tokens
    ↓
[Parser] → AST (Pipeline, DataCommand, Expr)
    ↓
[Interpreter] → Resolved AST (bind data streams, infer encoding)
    ↓
[Compiler] → Graph<Node>
    ↓
[Infer] → Validated Graph with type info
    ↓
[Program] (graph + metadata)
```

### Two Compilation Paths

1. **Text-based** (`Compiler.kt`): Parses TypeStream DSL commands
2. **Proto-based** (`GraphCompiler.kt`): Accepts UI-built pipeline graphs

### Node Types

The graph is composed of sealed interface `Node` types:

| Node | Purpose |
|------|---------|
| `StreamSource` | Reads from Kafka topic |
| `Filter` | Filters records by predicate |
| `Map` | Transforms records |
| `Join` | Joins two streams |
| `Group` | Groups by key |
| `Count` | Counts records |
| `Sink` | Writes to Kafka topic |
| `Each` | Side effects |

### Type System

Each `Node` type encapsulates its own schema transformation via `inferOutputSchema()`:
- Schema inference is co-located with node definitions in `Node.kt`
- Adding a new node type only requires implementing `inferOutputSchema()` in one place
- Validates schema compatibility at compile time

## Jobs

Jobs execute compiled programs as Kafka Streams topologies.

### Job Lifecycle

```
Program (compiled graph)
    ↓
Job Interface (sealed)
├── KafkaStreamsJob  (embedded)
└── K8sJob           (Kubernetes)
    ↓
Scheduler (coroutine-based)
    ↓
Output Stream (Flow<String>)
```

### Job States

```
STARTING → RUNNING → STOPPING → STOPPED
              ↓
           FAILED
```

### KafkaStreamsJob

The primary job executor for local/embedded mode:

1. Takes compiled `Program` and `KafkaConfig`
2. `buildTopology()` converts `Graph<Node>` to Kafka Streams topology
3. `start()` launches KafkaStreams instance
4. `output()` streams results from `-stdout` topic
5. `state()` maps KafkaStreams state to Job.State

### Scheduler

Manages job lifecycle with Kotlin coroutines:
- Channel-based job queue
- Thread-safe job collection
- Supports K8s mode for external job monitoring

## gRPC Services

| Service | Methods | Purpose |
|---------|---------|---------|
| FileSystemService | Mount, Unmount, Ls | Virtual filesystem operations |
| InteractiveSessionService | StartSession, RunProgram, GetProgramOutput | Interactive REPL |
| JobService | CreateJob, CreateJobFromGraph, ListJobs | Job management |
| ConnectionService | RegisterConnection, GetConnectionStatuses, TestConnection | Database connection monitoring |

## Virtual Filesystem

Kafka topics are exposed as a UNIX-like filesystem:

```
/dev/kafka/{cluster}/topics/{topic}
```

- **FileSystem.kt**: Root filesystem abstraction
- **Catalog**: Metadata cache (schemas from Schema Registry)
- Enables commands like `cat`, `ls`, `grep` on topics

## Avro Serialization

TypeStream uses Avro as its primary serialization format. The `kafka/` package handles serialization/deserialization with Schema Registry integration.

### Deserialization Flow

```
Kafka Message: [magic byte (0x00)] [schema ID (4 bytes)] [avro binary data]
                                          │
                                          ▼
                            Schema Registry (cached lookup)
                                          │
                                          ▼
                            AvroSerde.deserialize()
                            ├── Reads schema ID from message
                            ├── Fetches writer schema from registry
                            └── Deserializes using writer schema
                                          │
                                          ▼
                                    GenericRecord
```

### Key Design Decision

The deserializer fetches the **writer's schema** from Schema Registry using the schema ID embedded in each message. This enables TypeStream to read topics produced by external systems (like Debezium CDC) that use different schema namespaces.

### External Producer Support

TypeStream can consume Avro topics from any producer:

| Producer | Schema Example | Works? |
|----------|----------------|--------|
| TypeStream demo-data | `namespace: io.typestream.connectors.avro` | ✓ |
| Debezium CDC | `namespace: dbserver.public.users, name: Envelope` | ✓ |
| Confluent producers | Any valid Avro schema | ✓ |

### Key Files

| File | Purpose |
|------|---------|
| `AvroSerde.kt` | Kafka serde with schema registry lookup |
| `SchemaRegistryClient.kt` | HTTP client for Schema Registry API |
| `GenericDataWithLogicalTypes.kt` | Avro logical type support |

## Debezium Source (CDC)

TypeStream supports consuming Change Data Capture (CDC) events from Debezium. The `unwrapCdc` flag on `StreamSource` nodes extracts the "after" payload from CDC envelope structures.

### CDC Envelope Structure

Debezium emits CDC events with this schema:
```
{
  "before": { ... },    // Previous state (null for inserts)
  "after": { ... },     // New state (null for deletes)
  "source": { ... },    // Debezium metadata
  "op": "c|u|d|r",      // Operation type
  "ts_ms": 1234567890
}
```

### CDC Unwrapping Flow

```
Debezium Topic (CDC envelope)
        ↓
StreamSource(unwrapCdc=true)
        ↓
[GraphCompiler.unwrapCdcDataStream()]
  ├── Detects CDC envelope (before/after/source/op fields)
  ├── Extracts schema from "after" field
  └── Returns DataStream with unwrapped schema
        ↓
Downstream nodes receive flattened records
```

### Key Files

| File | Function |
|------|----------|
| `GraphCompiler.kt:unwrapCdcDataStream()` | Schema extraction from CDC envelope |
| `Node.kt:StreamSource.unwrapCdc` | Boolean flag to enable unwrapping |
| `AvroExt.kt` | Preserves null values for before/after fields |

### Proto Definition

```protobuf
message StreamSourceNode {
  DataStreamProto data_stream = 1;
  Encoding encoding = 2;
  bool unwrap_cdc = 3;  // Extract 'after' payload from CDC envelope
}
```

## Kafka Connect Sink (JDBC)

TypeStream creates JDBC sink connectors via Kafka Connect to write processed data to databases. The architecture uses a secure server-side credential resolution pattern.

### Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                          UI (Client)                            │
│  DbSinkNode configured with:                                    │
│   - connectionId (reference, no credentials)                    │
│   - tableName, insertMode, primaryKeyFields                     │
└───────────────────────────┬─────────────────────────────────────┘
                            │ CreateJobFromGraphRequest
                            │ (includes DbSinkConfig list)
                            ▼
┌─────────────────────────────────────────────────────────────────┐
│                     JobService.createJobFromGraph()             │
│  1. Compiles pipeline graph to Program                          │
│  2. Starts Kafka Streams job                                    │
│  3. For each DbSinkConfig:                                      │
│     - Generates intermediate topic name                         │
│     - Calls ConnectionService.createJdbcSinkConnector()         │
└───────────────────────────┬─────────────────────────────────────┘
                            │
                            ▼
┌─────────────────────────────────────────────────────────────────┐
│               ConnectionService.createJdbcSinkConnector()       │
│  1. Looks up connection by ID (has credentials)                 │
│  2. Builds JDBC connector config:                               │
│     - connector.class: io.debezium.connector.jdbc.JdbcSinkConnector
│     - connection.url: jdbc:postgresql://...                     │
│     - value.converter: AvroConverter                            │
│     - transforms.unwrap: ExtractNewRecordState                  │
│  3. POSTs config to Kafka Connect REST API                      │
└───────────────────────────┬─────────────────────────────────────┘
                            │ POST /connectors
                            ▼
┌─────────────────────────────────────────────────────────────────┐
│                      Kafka Connect                              │
│  JdbcSinkConnector consumes from intermediate topic             │
│  and writes to target database table                            │
└─────────────────────────────────────────────────────────────────┘
```

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
        ↓                                  ↓
[StreamSource] → [Transform] → [Sink]     [JdbcSinkConnector]
                                ↓              ↓
                        intermediate_topic ───────────→ Database Table
```

### Proto Definitions

```protobuf
// In job.proto - Sent from client (no credentials)
message DbSinkConfig {
  string node_id = 1;
  string connection_id = 2;        // Server resolves credentials
  string table_name = 3;
  string insert_mode = 4;          // insert, upsert, update
  string primary_key_fields = 5;
  string intermediate_topic = 6;   // Optional - auto-generated if empty
}

// In connection.proto - Internal server request
message CreateJdbcSinkConnectorRequest {
  string connection_id = 1;
  string connector_name = 2;
  string topics = 3;
  string table_name = 4;
  string insert_mode = 5;
  string primary_key_fields = 6;
}
```

### Connector Configuration

The JDBC sink connector is configured with:

| Config | Value |
|--------|-------|
| `connector.class` | `io.debezium.connector.jdbc.JdbcSinkConnector` |
| `value.converter` | `io.confluent.connect.avro.AvroConverter` |
| `transforms.unwrap.type` | `io.debezium.transforms.ExtractNewRecordState` |
| `schema.evolution` | `basic` |
| `insert.mode` | `insert`, `upsert`, or `update` |

### Rollback Handling

If connector creation fails, JobService performs rollback:

```kotlin
private fun rollbackJobAndConnectors(jobId: String, connectorNames: List<String>) {
    vm.scheduler.kill(jobId)
    connectorNames.forEach { deleteKafkaConnectConnector(it) }
}
```

### Key Files

| File | Function |
|------|----------|
| `JobService.kt:createJobFromGraph()` | Orchestrates job + connector creation |
| `ConnectionService.kt:createJdbcSinkConnector()` | Builds and deploys connector |
| `ConnectionService.kt:buildJdbcSinkConnectorConfig()` | Constructs connector JSON |
| `ConnectionService.kt:createKafkaConnectConnector()` | HTTP POST to Connect API |

## Key Files

| File | Purpose |
|------|---------|
| `Compiler.kt` | Text → Graph compilation |
| `GraphCompiler.kt` | Proto → Graph compilation |
| `Interpreter.kt` | AST traversal, type binding |
| `Node.kt` | Node types with schema inference |
| `KafkaStreamsJob.kt` | Topology builder, job execution |
| `Scheduler.kt` | Job queue and lifecycle |
| `Vm.kt` | Execution routing (KAFKA vs SHELL) |
| `ConnectionService.kt` | JDBC connection monitoring and sink connector management |
| `DataStreamSerde.kt` | Kafka serialization |
