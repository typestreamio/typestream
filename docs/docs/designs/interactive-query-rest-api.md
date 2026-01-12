# Interactive Query REST API

Query KTable state via REST, eliminating the need for separate databases/microservices for aggregations.

## Concept

```
[Wikipedia SSE] → [Kafka: wikipedia_changes] → [wc -b .user] → [KTable State Store]
                                                                       ↓
                                                       REST API: GET /api/v1/state/user-counts
```

**Value Proposition**: "No need to build another database or service - TypeStream handles the transformation AND exposes the results via REST"

---

## User Flow

### Option A: Simple API on Existing Jobs (MVP)

```bash
# Create job as normal (sink to Kafka topic)
cat /dev/kafka/local/topics/wikipedia_changes | wc -b .user > /dev/kafka/local/topics/user-counts

# Query the internal state store of any running job
curl http://localhost:8080/api/v1/jobs/{job-id}/state
curl http://localhost:8080/api/v1/jobs/{job-id}/state/SomeUsername
```

### Option B: Full /dev/rest Sink (Complete Vision)

```bash
# Explicit REST endpoint name
cat /dev/kafka/local/topics/wikipedia_changes | wc -b .user > /dev/rest/user-counts

# Query by friendly name
curl http://localhost:8080/api/v1/state/user-counts
curl http://localhost:8080/api/v1/state/user-counts/SomeUsername
```

---

## Scope Options Comparison

| Aspect | Option A (MVP) | Option B (Full) |
|--------|----------------|-----------------|
| UX | Job-ID based queries | Named endpoints |
| Files touched | ~5 files | ~8 files |
| Compiler changes | None | Yes |
| Filesystem changes | None | Add /dev/rest |
| New node type | None | RestSink |
| Demo-ready | Functional | Polished |

**Recommendation**: Start with Option A, then extend to Option B. The StateStoreRegistry and StateQueryService are needed for both.

---

## Implementation Steps

### Step 1: Proto - Add State Query Service

**New file**: `protos/src/main/proto/state_query.proto`

```protobuf
syntax = "proto3";

package io.typestream.grpc;

option java_package = "io.typestream.grpc.state_query_service";

import "google/api/annotations.proto";

service StateQueryService {
    rpc ListStores(ListStoresRequest) returns (ListStoresResponse) {
        option (google.api.http) = {
            get: "/api/v1/state"
        };
    }

    rpc GetAllValues(GetAllValuesRequest) returns (stream KeyValuePair) {
        option (google.api.http) = {
            get: "/api/v1/state/{store_name}"
        };
    }

    rpc GetValue(GetValueRequest) returns (GetValueResponse) {
        option (google.api.http) = {
            get: "/api/v1/state/{store_name}/{key}"
        };
    }
}

message ListStoresRequest {}

message StoreInfo {
    string name = 1;
    string job_id = 2;
    int64 approximate_count = 3;
}

message ListStoresResponse {
    repeated StoreInfo stores = 1;
}

message GetAllValuesRequest {
    string store_name = 1;
    int32 limit = 2;
    string from_key = 3;  // For pagination
}

message KeyValuePair {
    string key = 1;
    string value = 2;  // JSON representation
}

message GetValueRequest {
    string store_name = 1;
    string key = 2;
}

message GetValueResponse {
    bool found = 1;
    string value = 2;
}
```

### Step 2: Backend - StateStoreRegistry

**New file**: `server/src/main/kotlin/io/typestream/state/StateStoreRegistry.kt`

```kotlin
data class StateStoreInfo(
    val name: String,
    val jobId: String,
    val kafkaStreams: KafkaStreams,
    val storeName: String  // Internal Kafka Streams store name
)

class StateStoreRegistry {
    private val stores = ConcurrentHashMap<String, StateStoreInfo>()

    fun register(name: String, info: StateStoreInfo) {
        stores[name] = info
    }

    fun unregister(name: String) {
        stores.remove(name)
    }

    fun get(name: String): StateStoreInfo? = stores[name]

    fun list(): List<StateStoreInfo> = stores.values.toList()
}
```

### Step 3: Backend - Modify KafkaStreamsJob

**Modify**: `server/src/main/kotlin/io/typestream/scheduler/KafkaStreamsJob.kt`

Key changes:
1. Use `Materialized.as(storeName)` when building KTable from `wc -b`
2. Register state store with StateStoreRegistry after job starts
3. Unregister on stop/remove

```kotlin
// In count() method of KafkaStreamSource:
fun count(storeName: String) {
    requireNotNull(groupedStream) { "cannot count a non-grouped stream" }
    val materialized = Materialized.`as`<DataStream, Long, KeyValueStore<Bytes, byte[]>>(storeName)
    stream = groupedStream!!.count(materialized)
        .mapValues { v -> DataStream.fromLong("", v) }
        .toStream()
}

// After kafkaStreams.start():
stateStoreRegistry.register(
    name = "${program.id}-count-store",
    info = StateStoreInfo(
        name = "${program.id}-count-store",
        jobId = program.id,
        kafkaStreams = kafkaStreams,
        storeName = storeName
    )
)
```

### Step 4: Backend - StateQueryService

**New file**: `server/src/main/kotlin/io/typestream/server/StateQueryService.kt`

```kotlin
class StateQueryService(
    private val registry: StateStoreRegistry
) : StateQueryServiceGrpcKt.StateQueryServiceCoroutineImplBase() {

    override suspend fun listStores(request: ListStoresRequest): ListStoresResponse {
        val stores = registry.list().map { info ->
            StoreInfo.newBuilder()
                .setName(info.name)
                .setJobId(info.jobId)
                .setApproximateCount(getApproximateCount(info))
                .build()
        }
        return ListStoresResponse.newBuilder().addAllStores(stores).build()
    }

    override fun getAllValues(request: GetAllValuesRequest): Flow<KeyValuePair> = flow {
        val info = registry.get(request.storeName)
            ?: throw StatusException(Status.NOT_FOUND)

        val store = info.kafkaStreams.store(
            StoreQueryParameters.fromNameAndType(
                info.storeName,
                QueryableStoreTypes.keyValueStore<DataStream, DataStream>()
            )
        )

        store.all().use { iterator ->
            var count = 0
            while (iterator.hasNext() && count < (request.limit.takeIf { it > 0 } ?: 100)) {
                val kv = iterator.next()
                emit(KeyValuePair.newBuilder()
                    .setKey(kv.key.toString())
                    .setValue(kv.value.toString())
                    .build())
                count++
            }
        }
    }

    override suspend fun getValue(request: GetValueRequest): GetValueResponse {
        val info = registry.get(request.storeName)
            ?: throw StatusException(Status.NOT_FOUND)

        val store = info.kafkaStreams.store(
            StoreQueryParameters.fromNameAndType(
                info.storeName,
                QueryableStoreTypes.keyValueStore<DataStream, DataStream>()
            )
        )

        val key = DataStream.fromString("", request.key)
        val value = store.get(key)

        return GetValueResponse.newBuilder()
            .setFound(value != null)
            .setValue(value?.toString() ?: "")
            .build()
    }
}
```

### Step 5: Add gRPC-Gateway for REST

**Modify**: `server/build.gradle.kts`

Add grpc-gateway dependencies and configuration for generating REST endpoints from proto annotations.

**Modify**: `server/src/main/kotlin/io/typestream/Server.kt`

- Add StateStoreRegistry singleton
- Register StateQueryService with gRPC server
- Configure gRPC-Gateway HTTP proxy

---

## Option B Extensions (After MVP)

### Step 6: Add RestSink Node Type

**Modify**: `server/src/main/kotlin/io/typestream/compiler/node/Node.kt`

```kotlin
@Serializable
data class RestSink(
    override val id: String,
    val storeName: String,     // REST endpoint name
    val output: DataStream
) : Node
```

### Step 7: Extend FileSystem

**Modify**: `server/src/main/kotlin/io/typestream/filesystem/FileSystem.kt`

```kotlin
// In init:
private val restDir = Directory("rest")
devDir.add(restDir)  // /dev/rest/
```

**New file**: `server/src/main/kotlin/io/typestream/filesystem/RestEndpoint.kt`

```kotlin
class RestEndpoint(
    override val name: String,
    private val storeRegistry: StateStoreRegistry
) : Inode {
    override fun stat() = "rest endpoint: $name"
    override fun path() = "/dev/rest/$name"
}
```

### Step 8: Update Compiler

**Modify**: `server/src/main/kotlin/io/typestream/compiler/Compiler.kt`

Detect `/dev/rest/` paths in redirections and create `RestSink` nodes instead of regular `Sink` nodes.

---

## REST API Design

| Method | Path | Description |
|--------|------|-------------|
| GET | `/api/v1/state` | List all exposed state stores |
| GET | `/api/v1/state/{name}` | Get all key-value pairs (paginated) |
| GET | `/api/v1/state/{name}/{key}` | Get value for specific key |

### Response Examples

**List Stores**:
```json
{
  "stores": [
    {
      "name": "job-abc123-count-store",
      "jobId": "job-abc123",
      "approximateCount": 1500
    }
  ]
}
```

**Get All Values**:
```json
{
  "entries": [
    {"key": "alice", "value": "42"},
    {"key": "bob", "value": "17"}
  ]
}
```

**Get Value**:
```json
{
  "key": "alice",
  "value": "42",
  "found": true
}
```

---

## Critical Files

| Component | Path |
|-----------|------|
| State query proto | `protos/src/main/proto/state_query.proto` (new) |
| State registry | `server/src/main/kotlin/io/typestream/state/StateStoreRegistry.kt` (new) |
| State query service | `server/src/main/kotlin/io/typestream/server/StateQueryService.kt` (new) |
| KafkaStreamsJob | `server/src/main/kotlin/io/typestream/scheduler/KafkaStreamsJob.kt` |
| Server | `server/src/main/kotlin/io/typestream/Server.kt` |
| Node types | `server/src/main/kotlin/io/typestream/compiler/node/Node.kt` |
| FileSystem | `server/src/main/kotlin/io/typestream/filesystem/FileSystem.kt` |
| Compiler | `server/src/main/kotlin/io/typestream/compiler/Compiler.kt` |

---

## Technical Decisions

- **HTTP Layer**: gRPC-Gateway (auto-generate REST from proto annotations)
- **Single instance only** for MVP (no distributed query routing)
- **State stores re-register on job restart** (Kafka changelog provides durability)

---

## Future Enhancements

- Distributed query routing (query any instance, forward to correct partition owner)
- Range queries (from/to keys)
- Time-windowed state store queries
- WebSocket streaming for real-time updates
- UI integration for browsing state stores
