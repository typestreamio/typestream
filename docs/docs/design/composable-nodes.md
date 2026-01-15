# UI Schema Propagation

## Status

**In Progress** - Implementing server-side schema inference for UI field dropdowns.

## Problem

Downstream nodes can't populate field dropdowns when upstream is a transformation node (not a direct KafkaSource).

**Example that fails:**
```
KafkaSource(file_uploads) → TextExtractor → EmbeddingGenerator
                                                   ↓
                              EmbeddingGenerator.textField dropdown is empty
                              (can't see TextExtractor's output fields)
```

**Root cause:** `EmbeddingGeneratorNode.tsx` only looks for direct `kafkaSource` upstream:
```typescript
const topicPath = sourceNode?.type === 'kafkaSource'
  ? (sourceNode.data as KafkaSourceNodeData).topicPath
  : '';  // Empty for transformation nodes!
```

The server already computes schemas correctly via `GraphCompiler.inferNodeSchemasAndEncodings()` - we just need to expose it to the UI.

## Solution: Server-Side Graph Schema Inference

Add a new gRPC endpoint that reuses existing `GraphCompiler` logic. The UI calls this on graph changes and stores computed schemas in each node's data.

### Data Flow

```
User changes something (edge, config, etc.)
         ↓
UI calls InferGraphSchemas(graph)  // debounced 300ms
         ↓
Server runs GraphCompiler.inferNodeSchemasAndEncodings()
         ↓
Returns: {
  "source-1": {fields: ["id", "file_path", "name"], error: ""},
  "text-ext-1": {fields: ["id", "file_path", "name", "text"], error: ""},
  "embed-1": {fields: ["id", "file_path", "name", "text", "embedding"], error: ""}
}
         ↓
UI updates ALL nodes with their outputSchema
         ↓
Downstream nodes immediately see updated fields in dropdowns
```

## Implementation

### Phase 1: Proto Definition

Add to `protos/src/main/proto/job.proto`:

```protobuf
// Add to JobService
rpc InferGraphSchemas(InferGraphSchemasRequest) returns (InferGraphSchemasResponse);

message InferGraphSchemasRequest {
  PipelineGraph graph = 1;
}

message InferGraphSchemasResponse {
  map<string, NodeSchemaResult> schemas = 1;
}

message NodeSchemaResult {
  repeated string fields = 1;
  string encoding = 2;
  string error = 3;
}
```

### Phase 2: Server Implementation

**1. Make inference method public** in `GraphCompiler.kt`:

```kotlin
// Change from private to public
fun inferNodeSchemasAndEncodings(graph: Job.PipelineGraph): Pair<Map<String, DataStream>, Map<String, Encoding>>
```

**2. Implement endpoint** in `JobService.kt`:

```kotlin
override suspend fun inferGraphSchemas(request: Job.InferGraphSchemasRequest): Job.InferGraphSchemasResponse {
    val results = mutableMapOf<String, Job.NodeSchemaResult>()

    try {
        val (schemas, encodings) = graphCompiler.inferNodeSchemasAndEncodings(request.graph)

        request.graph.nodesList.forEach { node ->
            val nodeSchema = schemas[node.id]
            val nodeEncoding = encodings[node.id]

            results[node.id] = nodeSchemaResult {
                if (nodeSchema?.schema is Schema.Struct) {
                    fields += (nodeSchema.schema as Schema.Struct).value.map { it.name }
                }
                encoding = nodeEncoding?.name ?: "AVRO"
            }
        }
    } catch (e: Exception) {
        request.graph.nodesList.forEach { node ->
            results[node.id] = nodeSchemaResult { error = e.message ?: "Inference failed" }
        }
    }

    return inferGraphSchemasResponse { this.schemas.putAll(results) }
}
```

#### Testing with grpcurl

After implementing, test directly (server runs on port 4242):

```bash
# List services to verify endpoint exists
grpcurl -plaintext localhost:4242 list io.typestream.grpc.JobService

# Test with a simple graph (KafkaSource → TextExtractor)
grpcurl -plaintext -d '{
  "graph": {
    "nodes": [
      {"id": "src-1", "streamSource": {"dataStream": {"path": "/dev/kafka/local/topics/file_uploads"}}},
      {"id": "text-1", "textExtractor": {"filePathField": "file_path", "outputField": "text"}}
    ],
    "edges": [
      {"fromId": "src-1", "toId": "text-1"}
    ]
  }
}' localhost:4242 io.typestream.grpc.JobService/InferGraphSchemas
```

Expected response:
```json
{
  "schemas": {
    "src-1": {"fields": ["id", "file_path", "file_name", "content_type", "uploaded_by", "timestamp"], "encoding": "AVRO"},
    "text-1": {"fields": ["id", "file_path", "file_name", "content_type", "uploaded_by", "timestamp", "text"], "encoding": "AVRO"}
  }
}
```

Test error case (missing upstream):
```bash
grpcurl -plaintext -d '{
  "graph": {
    "nodes": [
      {"id": "text-1", "textExtractor": {"filePathField": "file_path", "outputField": "text"}}
    ],
    "edges": []
  }
}' localhost:4242 io.typestream.grpc.JobService/InferGraphSchemas
```

Expected response:
```json
{
  "schemas": {
    "text-1": {"error": "textExtractor text-1 missing input"}
  }
}
```

### Phase 3: UI - Node Validation State

**1. Add NodeValidationState to node types** in `uiv2/src/components/graph-builder/nodes/index.ts`:

```typescript
export interface NodeValidationState {
  outputSchema?: string[];
  schemaError?: string;
  isInferring?: boolean;
}

export interface GeoIpNodeData extends NodeValidationState {
  ipField: string;
  outputField: string;
}

export interface TextExtractorNodeData extends NodeValidationState {
  filePathField: string;
  outputField: string;
}

export interface EmbeddingGeneratorNodeData extends NodeValidationState {
  textField: string;
  outputField: string;
  model: string;
}

// ... similar for all node types
```

**2. Update BaseNode** in `uiv2/src/components/graph-builder/nodes/BaseNode.tsx`:

```typescript
interface BaseNodeProps {
  title: string;
  icon?: ReactNode;
  error?: string;
  isInferring?: boolean;
  children: ReactNode;
}

export function BaseNode({ title, icon, error, isInferring, children }: BaseNodeProps) {
  return (
    <Paper
      elevation={3}
      sx={{
        minWidth: 220,
        bgcolor: 'background.paper',
        border: error ? '2px solid' : '1px solid',
        borderColor: error ? 'error.main' : 'divider',
        opacity: isInferring ? 0.7 : 1,
      }}
    >
      <Box sx={{ /* header styles */ }}>
        {icon}
        <Typography variant="subtitle2" fontWeight="bold">{title}</Typography>
        {isInferring && <CircularProgress size={12} />}
        {error && (
          <Tooltip title={error}>
            <ErrorIcon color="error" fontSize="small" />
          </Tooltip>
        )}
      </Box>
      <Box sx={{ p: 1.5 }}>{children}</Box>
    </Paper>
  );
}
```

### Phase 4: UI - Inference Hook and Integration

**1. Create hook** in `uiv2/src/hooks/useInferGraphSchemas.ts`:

```typescript
import { useCallback } from 'react';
import { useMutation } from '@tanstack/react-query';
import { useJobServiceClient } from './useJobServiceClient';
import type { PipelineGraph } from '../generated/job_pb';

export function useInferGraphSchemas() {
  const client = useJobServiceClient();

  return useMutation({
    mutationFn: async (graph: PipelineGraph) => {
      const response = await client.inferGraphSchemas({ graph });
      return response.schemas;
    },
  });
}
```

**2. Integrate into GraphBuilder** in `uiv2/src/components/graph-builder/GraphBuilder.tsx`:

```typescript
const inferMutation = useInferGraphSchemas();

// Debounced inference on graph changes
useEffect(() => {
  if (nodes.length === 0) return;

  const timeout = setTimeout(async () => {
    // Mark nodes as inferring
    nodes.forEach(node => {
      setNodes(nds => nds.map(n =>
        n.id === node.id ? { ...n, data: { ...n.data, isInferring: true } } : n
      ));
    });

    try {
      const graph = serializeGraph(nodes, edges);
      const schemas = await inferMutation.mutateAsync(graph);

      // Update each node with its schema result
      Object.entries(schemas).forEach(([nodeId, result]) => {
        setNodes(nds => nds.map(n =>
          n.id === nodeId ? {
            ...n,
            data: {
              ...n.data,
              outputSchema: result.fields,
              schemaError: result.error || undefined,
              isInferring: false,
            }
          } : n
        ));
      });
    } catch (e) {
      nodes.forEach(node => {
        setNodes(nds => nds.map(n =>
          n.id === node.id ? { ...n, data: { ...n.data, schemaError: 'Inference failed', isInferring: false } } : n
        ));
      });
    }
  }, 300);

  return () => clearTimeout(timeout);
}, [nodes, edges]);
```

### Phase 5: UI - Update Transformation Nodes

**Update nodes to read upstream `outputSchema`** instead of calling `useTopicSchema`:

```typescript
// Before (EmbeddingGeneratorNode.tsx)
const topicPath = sourceNode?.type === 'kafkaSource' ? ... : '';
const { fields } = useTopicSchema(topicPath);

// After
const upstreamNode = incomingEdge ? nodes.find(n => n.id === incomingEdge.source) : null;
const fields = (upstreamNode?.data as NodeValidationState)?.outputSchema ?? [];
```

**Pass validation state to BaseNode:**

```typescript
<BaseNode
  title="Embedding Generator"
  icon={<MemoryIcon />}
  error={data.schemaError}
  isInferring={data.isInferring}
>
  {/* field selectors */}
</BaseNode>
```

**Nodes to update:**
- `EmbeddingGeneratorNode.tsx`
- `TextExtractorNode.tsx`
- `GeoIpNode.tsx`
- `MaterializedViewNode.tsx`
- `KafkaSourceNode.tsx`

## Files to Modify

| File | Change |
|------|--------|
| `protos/src/main/proto/job.proto` | Add `InferGraphSchemas` RPC + messages |
| `server/.../GraphCompiler.kt` | Make `inferNodeSchemasAndEncodings` public |
| `server/.../JobService.kt` | Implement `inferGraphSchemas` endpoint |
| `uiv2/.../nodes/index.ts` | Add `NodeValidationState` interface |
| `uiv2/.../nodes/BaseNode.tsx` | Add error/loading display |
| `uiv2/.../hooks/useInferGraphSchemas.ts` | New hook (create) |
| `uiv2/.../GraphBuilder.tsx` | Call inference, update node data |
| `uiv2/.../EmbeddingGeneratorNode.tsx` | Use upstream `outputSchema`, pass error to BaseNode |
| `uiv2/.../TextExtractorNode.tsx` | Use upstream `outputSchema`, pass error to BaseNode |
| `uiv2/.../GeoIpNode.tsx` | Use upstream `outputSchema`, pass error to BaseNode |
| `uiv2/.../MaterializedViewNode.tsx` | Use upstream `outputSchema`, pass error to BaseNode |
| `uiv2/.../KafkaSourceNode.tsx` | Pass error to BaseNode |

## Verification

1. **grpcurl test** - Verify endpoint returns correct schemas for chained transformations
2. **Manual test - field propagation:**
   - Create: `KafkaSource(file_uploads) → TextExtractor → EmbeddingGenerator`
   - Verify TextExtractor shows source fields (file_path, etc.)
   - Verify EmbeddingGenerator shows TextExtractor's output fields including `text`
3. **Manual test - error display:**
   - Disconnect edge mid-chain → downstream node shows red border + error icon
   - Hover error icon → tooltip shows "Missing upstream connection"
   - Reconnect → red border disappears, fields populate
4. **Manual test - loading state:**
   - Make change → nodes briefly show loading indicator
   - Inference completes → loading indicator disappears

## Future Considerations

### Handler/Registry Pattern (Deferred)

The current architecture requires modifying multiple central files to add new node types. A future refactor could adopt a handler + execution pattern where each node type is self-contained:

```
nodes/
├── geoip/
│   ├── GeoIpNodeHandler.kt   # Proto → Node + type inference
│   └── GeoIpExecution.kt     # Kafka + Shell execution
├── textextractor/
│   └── ...
```

This would allow adding new nodes without touching `GraphCompiler.kt`, `KafkaStreamsJob.kt`, or `Vm.kt`. However, the current approach works and this refactor can be done incrementally later.
