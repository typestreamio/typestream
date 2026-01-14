# Materialized View & Interactive Queries

## Overview

Add a configurable Materialized View node to the Graph Builder UI that materializes aggregation results as queryable state stores. The node supports multiple aggregation types with results accessible via the StateQueryService gRPC API.

## Demo Scenarios

1. `Kafka Source (wikipedia_changes)` → `Materialized View (COUNT by: user)` → Query author edit counts
2. `Kafka Source (coinbase)` → `Materialized View (LATEST by: product_id)` → Query latest exchange rates

**Value Proposition**: "No need to build another database or service - TypeStream handles the transformation AND exposes the results via gRPC"

---

## UI Behavior

- Terminal node (input handle only, no output)
- Dropdown for aggregation type: **Count** or **Latest Value**
- Dropdown of schema fields from upstream message (group by field)
- Shows table icon to indicate materialized state
- Job Detail page displays query endpoint and key count for running jobs

---

## Current State (Already Implemented)

- `state_query.proto` - Complete gRPC service definition
- `StateQueryService.kt` - Backend with ListStores, GetAllValues, GetValue
- `KafkaStreamsJob.kt` - Tracks state store names
- `KafkaStreamSource.kt` - COUNT materialization with named stores
- UI proto generation for state_query

---

## Implementation Steps

### Step 1: Fix GroupNode KeyMapper (Backend Blocker)

**File:** `server/src/main/kotlin/io/typestream/compiler/GraphCompiler.kt`

The GroupNode keyMapper is stubbed and ignores `key_mapper_expr`. Fix it to parse field paths.

```kotlin
// Current (broken) - around line 67-71:
proto.hasGroup() -> {
    val stubSchema = Schema.Struct(listOf())
    val stubDs = DataStream("/stub/key", stubSchema)
    Node.Group(proto.id) { _ -> stubDs }  // STUB!
}

// Fixed:
proto.hasGroup() -> {
    val fieldPath = proto.group.keyMapperExpr  // e.g., ".user" or ".product_id"
    val fields = fieldPath.trimStart('.').split('.').filter { it.isNotBlank() }
    Node.Group(proto.id) { kv -> kv.value.select(fields) }
}
```

Reference: `Wc.kt:22` shows field selection pattern.

---

### Step 2: Add ReduceLatest Node Type (Backend)

**File:** `server/src/main/kotlin/io/typestream/compiler/node/Node.kt`

```kotlin
@Serializable
data class ReduceLatest(override val id: String) : Node
```

**File:** `protos/src/main/proto/job.proto`

```protobuf
message ReduceLatestNode {}

// In PipelineNode oneof (next available field number):
ReduceLatestNode reduce_latest = 13;
```

---

### Step 3: Implement ReduceLatest in KafkaStreamSource (Backend)

**File:** `server/src/main/kotlin/io/typestream/compiler/kafka/KafkaStreamSource.kt`

```kotlin
private var reduceStoreNames = mutableListOf<String>()

fun getReduceStoreNames(): List<String> = reduceStoreNames.toList()

fun reduceLatest(storeName: String) {
    requireNotNull(groupedStream) { "cannot reduce a non-grouped stream" }

    reduceStoreNames.add(storeName)
    val materialized = Materialized.`as`<DataStream, DataStream, KeyValueStore<Bytes, ByteArray>>(storeName)
    stream = groupedStream!!.reduce(
        { _, newValue -> newValue },  // Keep latest value
        materialized
    ).toStream()
}
```

---

### Step 4: Wire ReduceLatest in Job Topology (Backend)

**File:** `server/src/main/kotlin/io/typestream/scheduler/KafkaStreamsJob.kt`

Add handling in `buildTopology()`:

```kotlin
is Node.ReduceLatest -> {
    val storeName = "${program.id}-reduce-store-$reduceStoreIndex"
    reduceStoreIndex++
    kafkaStreamSource.reduceLatest(storeName)
    stateStoreNames.add(storeName)
}
```

---

### Step 5: Update GraphCompiler for ReduceLatest (Backend)

**File:** `server/src/main/kotlin/io/typestream/compiler/GraphCompiler.kt`

Add case in `mapPipelineNode()`:
```kotlin
proto.hasReduceLatest() -> Node.ReduceLatest(proto.id)
```

Add inference in `inferNodeType()`:
```kotlin
proto.hasReduceLatest() -> {
    val out = input ?: error("reduce_latest $nodeId missing input")
    out to (inputEncoding ?: Encoding.AVRO)
}
```

---

### Step 6: Update Infer.kt for ReduceLatest (Backend)

**File:** `server/src/main/kotlin/io/typestream/compiler/Infer.kt`

Add `ReduceLatest` case to the exhaustive `when` expression:

```kotlin
is Node.ReduceLatest -> input ?: error("reduceLatest ${ref.id} missing input stream")
```

---

### Step 7: Update StateQueryService for DataStream Values (Backend)

**File:** `server/src/main/kotlin/io/typestream/server/StateQueryService.kt`

The current implementation hardcodes `KeyValueStore<DataStream, Long>` for count stores. For LATEST_VALUE, we need `KeyValueStore<DataStream, DataStream>`.

**Implementation:** Use naming convention - count stores contain "count", reduce stores contain "reduce". Query with appropriate type based on name:

```kotlin
// In listStores(), getAllValues(), and getValue():
if (storeName.contains("reduce")) {
    val store = kafkaStreams.store(
        StoreQueryParameters.fromNameAndType(
            storeName,
            QueryableStoreTypes.keyValueStore<DataStream, DataStream>()
        )
    )
    // Handle DataStream values
} else {
    val store = kafkaStreams.store(
        StoreQueryParameters.fromNameAndType(
            storeName,
            QueryableStoreTypes.keyValueStore<DataStream, Long>()
        )
    )
    // Handle Long values
}
```

---

### Step 8: Regenerate UI Protos

```bash
cd uiv2 && npm run proto
```

---

### Step 9: Create MaterializedViewNode Component (UI)

**File:** `uiv2/src/components/graph-builder/nodes/MaterializedViewNode.tsx` (NEW)

```typescript
import { Handle, Position, useReactFlow, useNodes, useEdges, type NodeProps } from '@xyflow/react';
import { FormControl, InputLabel, Select, MenuItem } from '@mui/material';
import TableChartIcon from '@mui/icons-material/TableChart';
import { BaseNode } from './BaseNode';
import { useTopicSchema } from '../../../hooks/useTopicSchema';
import type { MaterializedViewNodeType, KafkaSourceNodeData } from './index';

export type AggregationType = 'count' | 'latest';

export function MaterializedViewNode({ id, data }: NodeProps<MaterializedViewNodeType>) {
  const { updateNodeData } = useReactFlow();
  const nodes = useNodes();
  const edges = useEdges();

  // Find upstream source to get schema
  const incomingEdge = edges.find((e) => e.target === id);
  const sourceNode = incomingEdge ? nodes.find((n) => n.id === incomingEdge.source) : null;
  const topicPath = sourceNode?.type === 'kafkaSource'
    ? (sourceNode.data as KafkaSourceNodeData).topicPath
    : '';

  const { fields } = useTopicSchema(topicPath);

  return (
    <>
      <Handle type="target" position={Position.Left} />
      <BaseNode title="Materialized View" icon={<TableChartIcon fontSize="small" />}>
        <FormControl fullWidth size="small" sx={{ mb: 1 }}>
          <InputLabel>Aggregation</InputLabel>
          <Select
            value={data.aggregationType}
            label="Aggregation"
            onChange={(e) => updateNodeData(id, { aggregationType: e.target.value })}
            className="nodrag nowheel"
          >
            <MenuItem value="count">Count</MenuItem>
            <MenuItem value="latest">Latest Value</MenuItem>
          </Select>
        </FormControl>
        <FormControl fullWidth size="small">
          <InputLabel>Group By Field</InputLabel>
          <Select
            value={data.groupByField}
            label="Group By Field"
            onChange={(e) => updateNodeData(id, { groupByField: e.target.value })}
            className="nodrag nowheel"
          >
            {fields.map((field) => (
              <MenuItem key={field} value={field}>{field}</MenuItem>
            ))}
          </Select>
        </FormControl>
      </BaseNode>
    </>
  );
}
```

---

### Step 10: Register Node Type (UI)

**File:** `uiv2/src/components/graph-builder/nodes/index.ts`

```typescript
import { MaterializedViewNode, type AggregationType } from './MaterializedViewNode';

export interface MaterializedViewNodeData extends Record<string, unknown> {
  aggregationType: AggregationType;
  groupByField: string;
}

export type MaterializedViewNodeType = Node<MaterializedViewNodeData, 'materializedView'>;

// Update AppNode union
export type AppNode = KafkaSourceNodeType | KafkaSinkNodeType | GeoIpNodeType | MaterializedViewNodeType;

// Update nodeTypes registry
export const nodeTypes: NodeTypes = {
  kafkaSource: KafkaSourceNode,
  kafkaSink: KafkaSinkNode,
  geoIp: GeoIpNode,
  materializedView: MaterializedViewNode,
};
```

---

### Step 11: Add to Node Palette (UI)

**File:** `uiv2/src/components/graph-builder/NodePalette.tsx`

```typescript
import TableChartIcon from '@mui/icons-material/TableChart';

// Add palette item:
<PaletteItem
  type="materializedView"
  label="Materialized View"
  icon={<TableChartIcon fontSize="small" />}
/>
```

---

### Step 12: Update Graph Serializer (UI)

**File:** `uiv2/src/utils/graphSerializer.ts`

Materialized View serializes as **Group + (Count | ReduceLatest)**:

```typescript
import { GroupNode, CountNode, ReduceLatestNode } from '../generated/job_pb';

// In serializeGraph():
if (node.type === 'materializedView') {
  const data = node.data as MaterializedViewNodeData;
  const groupId = `${node.id}-group`;

  // Create Group node
  pipelineNodes.push(new PipelineNode({
    id: groupId,
    nodeType: {
      case: 'group',
      value: new GroupNode({ keyMapperExpr: `.${data.groupByField}` }),
    },
  }));

  // Create aggregation node based on type
  if (data.aggregationType === 'count') {
    pipelineNodes.push(new PipelineNode({
      id: node.id,
      nodeType: { case: 'count', value: new CountNode({}) },
    }));
  } else {
    pipelineNodes.push(new PipelineNode({
      id: node.id,
      nodeType: { case: 'reduceLatest', value: new ReduceLatestNode({}) },
    }));
  }

  // Internal edge from group to aggregation
  pipelineEdges.push(new PipelineEdge({ fromId: groupId, toId: node.id }));
  return;
}

// Update edge handling - redirect edges targeting materializedView to its group node
edges.forEach((edge) => {
  const targetNode = nodes.find((n) => n.id === edge.target);
  const toId = targetNode?.type === 'materializedView' ? `${edge.target}-group` : edge.target;
  pipelineEdges.push(new PipelineEdge({ fromId: edge.source, toId }));
});
```

---

### Step 13: Handle onDrop in GraphBuilder (UI)

**File:** `uiv2/src/components/graph-builder/GraphBuilder.tsx`

```typescript
// In onDrop handler:
const data = type === 'kafkaSource' ? { topicPath: '' }
  : type === 'kafkaSink' ? { topicName: '' }
  : type === 'geoIp' ? { ipField: '', outputField: 'country_code' }
  : type === 'materializedView' ? { aggregationType: 'count', groupByField: '' }
  : {};
```

---

### Step 14: Create useListStores Hook (UI)

**File:** `uiv2/src/hooks/useListStores.ts` (NEW)

```typescript
import { useQuery } from '@connectrpc/connect-query';
import { StateQueryService } from '../generated/state_query_connect';

export function useListStores() {
  return useQuery(
    { ...StateQueryService.methods.listStores, service: StateQueryService },
    {}
  );
}
```

---

### Step 15: Display State Stores on Job Detail Page (UI)

**File:** `uiv2/src/pages/JobDetailPage.tsx`

Add state stores section for running jobs:

```typescript
import { useListStores } from '../hooks/useListStores';

// In component:
const { data: storesData } = useListStores();
const jobStores = storesData?.stores.filter((s) => s.jobId === jobId) ?? [];

// In JSX (after Pipeline Graph section):
{job.state === JobState.RUNNING && jobStores.length > 0 && (
  <>
    <Divider sx={{ my: 2 }} />
    <Typography variant="h6" gutterBottom>State Stores</Typography>
    {jobStores.map((store) => (
      <Box key={store.name} sx={{ mb: 2 }}>
        <Typography variant="subtitle2">{store.name}</Typography>
        <Typography variant="body2">~{store.approximateCount.toString()} keys</Typography>
        <Typography variant="caption" component="pre" sx={{ mt: 1, p: 1, bgcolor: 'grey.900', borderRadius: 1 }}>
{`grpcurl -plaintext -d '{"store_name":"${store.name}","limit":10}' \\
  localhost:8080 io.typestream.grpc.StateQueryService/GetAllValues \\
  | jq '{key: .key | fromjson, value: .value | fromjson}'`}
        </Typography>
      </Box>
    ))}
  </>
)}
```

---

## Critical Files Summary

| Layer | File | Change |
|-------|------|--------|
| Proto | `protos/src/main/proto/job.proto` | Add ReduceLatestNode |
| Server | `server/.../compiler/GraphCompiler.kt` | Fix GroupNode, add ReduceLatest |
| Server | `server/.../compiler/node/Node.kt` | Add ReduceLatest node |
| Server | `server/.../compiler/Infer.kt` | Add ReduceLatest case |
| Server | `server/.../compiler/types/TypeRules.kt` | Add inferReduceLatest() |
| Server | `server/.../compiler/kafka/KafkaStreamSource.kt` | Add reduceLatest() |
| Server | `server/.../scheduler/KafkaStreamsJob.kt` | Handle ReduceLatest |
| Server | `server/.../server/StateQueryService.kt` | Support DataStream values |
| UI | `uiv2/.../nodes/MaterializedViewNode.tsx` | NEW |
| UI | `uiv2/.../nodes/index.ts` | Type registration |
| UI | `uiv2/.../NodePalette.tsx` | Add palette item |
| UI | `uiv2/.../graphSerializer.ts` | Serialize as Group+(Count\|ReduceLatest) |
| UI | `uiv2/.../GraphBuilder.tsx` | Handle onDrop |
| UI | `uiv2/src/hooks/useListStores.ts` | NEW |
| UI | `uiv2/src/pages/JobDetailPage.tsx` | Display state stores |

---

## Verification

1. **Start docker:** `cd cli && ./typestream local dev start`
2. **Start server:** `./scripts/dev/server.sh`
3. **Seed data:** `./scripts/dev/seed.sh`
4. **Start UI:** `cd uiv2 && npm run dev`

### Test Case 1: COUNT (Wikipedia)
1. Create pipeline: `Kafka Source (wikipedia_changes)` → `Materialized View (COUNT by: user)`
2. Start the job
3. Query:
   ```bash
   grpcurl -plaintext localhost:8080 io.typestream.grpc.StateQueryService/ListStores
   grpcurl -plaintext -d '{"store_name":"<store>","limit":10}' \
     localhost:8080 io.typestream.grpc.StateQueryService/GetAllValues \
     | jq '{key: .key | fromjson, value: .value | fromjson}'
   ```
4. Verify counts by user are returned

### Test Case 2: LATEST VALUE (Crypto)
1. Create pipeline: `Kafka Source (coinbase)` → `Materialized View (LATEST by: product_id)`
2. Start the job
3. Query the state store
4. Verify latest exchange rates per product_id are returned
