# GeoIP Lookup Transformation Node

## Overview

Add a GeoIP transformation node to TypeStream that performs local IP-to-country lookups using MaxMind GeoLite2 database. The node works with both Shell (demo) and Kafka Streams (production) runtimes.

## UI Behavior

- Dropdown of schema fields from upstream message (to select IP field)
- Text field for output field name (default: `country_code`)
- Shows globe/public icon to indicate geo operation

---

## Implementation Steps

### 1. Proto Definitions

**File:** `protos/src/main/proto/job.proto`

Add GeoIpNode message:

```protobuf
message GeoIpNode {
  string ip_field = 1;      // Field containing IP address
  string output_field = 2;  // Output field name (default: country_code)
}
```

Add to PipelineNode oneof:

```protobuf
GeoIpNode geo_ip = 12;
```

### 2. Add Schema Endpoint (for field dropdown)

**File:** `protos/src/main/proto/filesystem.proto`

```protobuf
rpc GetSchema(GetSchemaRequest) returns (GetSchemaResponse) {}

message GetSchemaRequest {
  string user_id = 1;
  string path = 2;  // Topic path
}

message GetSchemaResponse {
  repeated string fields = 1;
}
```

**File:** `server/src/main/kotlin/io/typestream/grpc/FileSystemServiceServer.kt`

- Implement `getSchema()` to return field names from topic schema

---

### 3. Server Node Type

**File:** `server/src/main/kotlin/io/typestream/compiler/node/Node.kt`

```kotlin
@Serializable
data class GeoIp(
    override val id: String,
    val ipField: String,
    val outputField: String
) : Node
```

---

### 4. MaxMind GeoIP Service

MaxMind GeoLite2 is a **local database file** (`.mmdb`) - no API calls, purely local lookups.

**File:** `server/src/main/kotlin/io/typestream/geoip/GeoIpService.kt` (new)

```kotlin
class GeoIpService(private val databasePath: String) {
    private val reader: DatabaseReader? by lazy { /* load mmdb file */ }

    fun lookup(ipAddress: String): String? {
        return reader?.country(InetAddress.getByName(ipAddress))
            ?.country?.isoCode
    }
}
```

**File:** `server/build.gradle.kts`

```kotlin
implementation("com.maxmind.geoip2:geoip2:4.2.0")
```

**File:** `config/src/main/kotlin/io/typestream/config/Config.kt`

```kotlin
data class GeoIpConfig(
    val databasePath: String = "/etc/typestream/GeoLite2-Country.mmdb"
)
```

---

### 5. Graph Compiler Updates

**File:** `server/src/main/kotlin/io/typestream/compiler/GraphCompiler.kt`

In `mapPipelineNode()`:

```kotlin
proto.hasGeoIp() -> {
    val g = proto.geoIp
    Node.GeoIp(proto.id, g.ipField, g.outputField)
}
```

In `inferNodeType()`:

```kotlin
proto.hasGeoIp() -> {
    val inputSchema = input?.schema as? Schema.Struct
    val newFields = inputSchema.value + Schema.Field(proto.geoIp.outputField, Schema.String.zeroValue)
    input.copy(schema = Schema.Struct(newFields)) to (inputEncoding ?: Encoding.AVRO)
}
```

---

### 6. Runtime Implementations

**File:** `server/src/main/kotlin/io/typestream/compiler/vm/Vm.kt` (Shell runtime)

```kotlin
is Node.GeoIp -> dataStreams.map { ds ->
    val ipValue = ds.selectField(node.ref.ipField) ?: ""
    val countryCode = geoIpService.lookup(ipValue) ?: "UNKNOWN"
    ds.addField(node.ref.outputField, countryCode)
}
```

**File:** `server/src/main/kotlin/io/typestream/compiler/kafka/KafkaStreamSource.kt`

```kotlin
fun geoIp(geoIp: Node.GeoIp) {
    stream = stream.mapValues { value ->
        val ip = value.selectField(geoIp.ipField)
        val country = geoIpService.lookup(ip) ?: "UNKNOWN"
        value.addField(geoIp.outputField, country)
    }
}
```

**File:** `server/src/main/kotlin/io/typestream/scheduler/KafkaStreamsJob.kt`

```kotlin
is Node.GeoIp -> kafkaStreamSource.geoIp(currentNode.ref)
```

---

### 7. UI Implementation

**File:** `uiv2/src/hooks/useTopicSchema.ts` (new)

```typescript
export function useTopicSchema(topicPath: string) {
  return useQuery(FileSystemService.methods.getSchema, { path: topicPath });
}
```

**File:** `uiv2/src/components/graph-builder/nodes/GeoIpNode.tsx` (new)

- Dropdown for IP field selection (populated from upstream schema)
- Text field for output field name (default: country_code)
- Uses BaseNode wrapper with PublicIcon

**File:** `uiv2/src/components/graph-builder/nodes/index.ts`

```typescript
export interface GeoIpNodeData {
  ipField: string;
  outputField: string;
}
export type GeoIpNodeType = Node<GeoIpNodeData, 'geoIp'>;
// Add to AppNode union and nodeTypes registry
```

**File:** `uiv2/src/components/graph-builder/NodePalette.tsx`

- Add GeoIp palette item with PublicIcon

**File:** `uiv2/src/utils/graphSerializer.ts`

```typescript
if (node.type === 'geoIp') {
  return new PipelineNode({
    id: node.id,
    nodeType: { case: 'geoIp', value: new GeoIpNode({...}) }
  });
}
```

**File:** `uiv2/src/components/graph-builder/GraphBuilder.tsx`

- Add default data for geoIp node in onDrop

---

### 8. Database Provisioning

**File:** `cli/pkg/compose/compose.yml`

```yaml
services:
  server:
    volumes:
      - ./data/GeoLite2-Country.mmdb:/etc/typestream/GeoLite2-Country.mmdb:ro
```

Download from MaxMind (requires free account): https://dev.maxmind.com/geoip/geolite2-free-geolocation-data

---

## Critical Files Summary

| Layer | File | Change |
|-------|------|--------|
| Proto | `protos/src/main/proto/job.proto` | Add GeoIpNode message |
| Proto | `protos/src/main/proto/filesystem.proto` | Add GetSchema RPC |
| Server | `server/src/main/kotlin/io/typestream/compiler/node/Node.kt` | Add Node.GeoIp |
| Server | `server/src/main/kotlin/io/typestream/geoip/GeoIpService.kt` | MaxMind wrapper (new) |
| Server | `server/src/main/kotlin/io/typestream/compiler/GraphCompiler.kt` | Handle GeoIp node |
| Server | `server/src/main/kotlin/io/typestream/compiler/vm/Vm.kt` | Shell runtime |
| Server | `server/src/main/kotlin/io/typestream/compiler/kafka/KafkaStreamSource.kt` | Kafka runtime |
| Server | `server/src/main/kotlin/io/typestream/scheduler/KafkaStreamsJob.kt` | Topology builder |
| Server | `server/src/main/kotlin/io/typestream/grpc/FileSystemServiceServer.kt` | GetSchema impl |
| UI | `uiv2/src/components/graph-builder/nodes/GeoIpNode.tsx` | Node component (new) |
| UI | `uiv2/src/components/graph-builder/nodes/index.ts` | Type registration |
| UI | `uiv2/src/utils/graphSerializer.ts` | Serialization |
| Config | `server/build.gradle.kts` | MaxMind dependency |
| Config | `config/src/main/kotlin/io/typestream/config/Config.kt` | GeoIpConfig |

---

## Implementation Order

1. Proto changes + regenerate (buf generate / pnpm proto)
2. Server: GeoIpService + Config
3. Server: Node.GeoIp + GraphCompiler
4. Server: Shell runtime (Vm.kt)
5. Server: Kafka runtime (KafkaStreamSource + KafkaStreamsJob)
6. Server: GetSchema RPC
7. UI: Node component + hooks
8. UI: Palette + serializer + types
9. Test with web_visits demo data
