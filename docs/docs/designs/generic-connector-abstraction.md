# Generic Datastore-to-Datastore Solution

## Summary

Design a **Kafka Connect-first** generic connector abstraction that supports multiple source and sink types. Leverage the Kafka Connect ecosystem for all external integrations while maintaining TypeStream's security model (server-side credential resolution).

## Target Connectors

**Sinks (Priority):**
- JDBC (current - Postgres, MySQL)
- Elasticsearch
- S3/Object storage
- MongoDB/NoSQL

**Sources (Future):**
- Keep existing Kafka topics + Debezium CDC
- Direct JDBC query sources
- S3/Object storage
- Other message queues

---

## Architecture

### Unified Connector Flow

```
┌─────────────────────────────────────────────────────────────────┐
│                          UI (Client)                            │
│  Sink Node (any type) configured with:                          │
│   - connectionId (reference, no credentials)                    │
│   - type-specific config (tableName, indexName, bucket, etc.)   │
└───────────────────────────┬─────────────────────────────────────┘
                            │ CreateJobFromGraphRequest
                            │ (includes SinkConnectorConfig[])
                            ▼
┌─────────────────────────────────────────────────────────────────┐
│                     JobService.createJobFromGraph()             │
│  1. Compiles pipeline graph to Program                          │
│  2. Starts Kafka Streams job                                    │
│  3. For each SinkConnectorConfig:                               │
│     - Resolves connection credentials                           │
│     - Dispatches to appropriate ConnectorBuilder                │
│     - Creates Kafka Connect connector via REST API              │
└───────────────────────────┬─────────────────────────────────────┘
                            │
            ┌───────────────┼───────────────┬───────────────┐
            ▼               ▼               ▼               ▼
┌───────────────┐ ┌───────────────┐ ┌───────────────┐ ┌───────────────┐
│ JdbcBuilder   │ │ ElasticBuilder│ │ S3Builder     │ │ MongoDbBuilder│
└───────────────┘ └───────────────┘ └───────────────┘ └───────────────┘
                            │
                            ▼ POST /connectors
                  Kafka Connect Cluster
```

### Security Model (Preserved)

```
Client → Server: connectionId + type-specific config (no secrets)
Server: Lookup credentials from connections registry
Server → Kafka Connect: Full config with credentials
```

---

## Implementation Phases

### Phase 1: Generalize Existing JDBC Support (Foundation)

Refactor current JDBC-specific code to use generic abstraction.

**Proto Changes:**

```protobuf
// New generic types in connection.proto
enum ConnectorType {
  CONNECTOR_TYPE_UNSPECIFIED = 0;
  JDBC_SINK = 1;
  ELASTICSEARCH_SINK = 2;
  S3_SINK = 3;
  MONGODB_SINK = 4;
}

message ConnectorConnectionConfig {
  string id = 1;
  string name = 2;
  ConnectorType connector_type = 3;

  oneof type_config {
    JdbcConnectionConfig jdbc = 10;
    ElasticsearchConnectionConfig elasticsearch = 11;
    S3ConnectionConfig s3 = 12;
    MongoDbConnectionConfig mongodb = 13;
  }
}

// In job.proto - replaces DbSinkConfig
message SinkConnectorConfig {
  string node_id = 1;
  string connection_id = 2;
  string intermediate_topic = 3;

  oneof type_config {
    JdbcSinkTypeConfig jdbc = 10;
    ElasticsearchSinkTypeConfig elasticsearch = 11;
    S3SinkTypeConfig s3 = 12;
    MongoDbSinkTypeConfig mongodb = 13;
  }
}
```

**Server Changes:**

```kotlin
// New builder pattern in ConnectionService
interface SinkConnectorBuilder {
    fun build(connection: ConnectorConnectionConfig, sinkConfig: SinkConnectorConfig): Map<String, Any>
}

class JdbcSinkConnectorBuilder : SinkConnectorBuilder { ... }
class ElasticsearchSinkConnectorBuilder : SinkConnectorBuilder { ... }
// etc.
```

**Files to modify:**
- `protos/src/main/proto/connection.proto`
- `protos/src/main/proto/job.proto`
- `server/src/main/kotlin/io/typestream/server/ConnectionService.kt`
- `uiv2/src/utils/graphSerializer.ts`

### Phase 2: Add Elasticsearch Sink

Prove the abstraction works with a second sink type.

**Proto additions:**
```protobuf
message ElasticsearchConnectionConfig {
  string connection_url = 1;
  string username = 2;
  string password = 3;
}

message ElasticsearchSinkTypeConfig {
  string index_name = 1;
  string write_method = 2;  // INSERT or UPSERT
  string document_id_field = 3;
}
```

**Files to create:**
- `uiv2/src/components/graph-builder/nodes/sinks/ElasticsearchSinkNode.tsx`

**Files to modify:**
- Proto files
- `uiv2/src/components/graph-builder/NodePalette.tsx`
- `cli/pkg/compose/` (add Elasticsearch service)

### Phase 3: Add S3 Sink

Object storage support (different pattern - no persistent connection).

**Proto additions:**
```protobuf
message S3ConnectionConfig {
  string region = 1;
  string bucket_name = 2;
  string access_key_id = 3;
  string secret_access_key = 4;
  string endpoint = 5;  // Optional for S3-compatible (MinIO)
}

message S3SinkTypeConfig {
  string topics_dir = 1;
  string flush_size = 2;
  string format = 3;      // json, avro, parquet
}
```

### Phase 4: Add MongoDB Sink

NoSQL document store support.

**Proto additions:**
```protobuf
message MongoDbConnectionConfig {
  string connection_uri = 1;
  string database = 2;
}

message MongoDbSinkTypeConfig {
  string collection = 1;
  string write_model_strategy = 2;
  string document_id_field = 3;
}
```

### Phase 5: Connection Management UI

Unified connection management for all connector types.

- Redesign `ConnectionsPage` to support multiple connector types
- Dynamic form rendering based on connector type
- Show connector type icons in connection list

### Phase 6: Source Connectors (Future)

Support Kafka Connect source connectors for:
- JDBC Source (query-based polling)
- S3 Source (read files)
- MongoDB Source (change streams)

---

## Key Design Decisions

| Decision | Choice | Rationale |
|----------|--------|-----------|
| Integration pattern | Kafka Connect | Mature connectors, single operational model, exactly-once semantics |
| Data flow | Always use intermediate topics | Decouples TypeStream from sink concerns, enables replay |
| Proto structure | `oneof` for type-specific configs | Type-safe, easy to add new types |
| Health monitoring | Type-specific checkers | JDBC=connection, ES=HTTP ping, S3=none needed |

---

## Critical Files to Modify

| File | Changes |
|------|---------|
| `protos/src/main/proto/connection.proto` | Add `ConnectorType`, `ConnectorConnectionConfig`, type-specific configs |
| `protos/src/main/proto/job.proto` | Add `SinkConnectorConfig` with `oneof type_config` |
| `server/src/main/kotlin/io/typestream/server/ConnectionService.kt` | Builder pattern, dispatch by type |
| `server/src/main/kotlin/io/typestream/server/JobService.kt` | Handle generic `SinkConnectorConfig` |
| `uiv2/src/components/graph-builder/nodes/index.ts` | Discriminated union for sink types |
| `uiv2/src/utils/graphSerializer.ts` | Serialize generic `SinkConnectorConfig` |
| `uiv2/src/components/graph-builder/NodePalette.tsx` | Show sinks by connector type |

---

## Verification Plan

1. **Unit Tests:**
   - Each builder produces valid Kafka Connect config
   - Proto round-trip serialization for all config types

2. **Integration Tests:**
   - End-to-end for each sink type: create job → verify data reaches sink
   - Rollback on connector creation failure

3. **Local Testing:**
   - Add Elasticsearch, MinIO, MongoDB to docker-compose
   - Manual testing of each sink type through UI

---

## Backward Compatibility

1. Keep existing `DbSinkConfig` proto field (deprecated)
2. Server accepts both old and new formats
3. UI migrates to new format immediately
4. Remove deprecated field in future release
