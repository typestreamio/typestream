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

`TypeRules.kt` is the single source of truth for type transformations:
- Centralizes type inference rules
- Ensures consistency across compilation paths
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

## Key Files

| File | Purpose |
|------|---------|
| `Compiler.kt` | Text → Graph compilation |
| `GraphCompiler.kt` | Proto → Graph compilation |
| `Interpreter.kt` | AST traversal, type binding |
| `TypeRules.kt` | Centralized type inference |
| `KafkaStreamsJob.kt` | Topology builder, job execution |
| `Scheduler.kt` | Job queue and lifecycle |
| `Vm.kt` | Execution routing (KAFKA vs SHELL) |
| `DataStreamSerde.kt` | Kafka serialization |
