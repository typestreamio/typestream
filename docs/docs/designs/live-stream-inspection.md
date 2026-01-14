# Live Stream Inspection Feature

Inspect live data flowing through any point in a pipeline using a dedicated **Inspector node**.

## Concept

```
[Source] → [Filter] → [Map] → [Sink]
                ↓
          [Inspector]  ← connect to any point, click eye to preview
```

- **Inspector node**: A special sink-like node you connect to any point in the graph
- **Ephemeral job**: Clicking "preview" creates a temporary job from the current (unsaved) graph
- **No save required**: Preview works directly from GraphBuilder without submitting the job

---

## User Flow

1. User builds graph in GraphBuilder
2. Drags "Inspector" node from palette, connects it to desired point
3. Clicks eye button on Inspector node (or "Preview" button)
4. System creates ephemeral job from current graph state
5. Inspector panel opens, streaming live messages
6. When user closes panel or leaves page, ephemeral job is stopped/cleaned up

---

## Implementation Steps

### Step 1: Proto - Add Ephemeral Job Support

**Modify**: `protos/src/main/proto/job.proto`

```protobuf
// Add to CreateJobFromGraphRequest or new message
message CreatePreviewJobRequest {
  PipelineGraph graph = 1;
  string inspector_node_id = 2;  // Which inspector node triggered this
}

message CreatePreviewJobResponse {
  string job_id = 1;
  string inspect_topic = 2;  // Topic to consume for preview data
}

// Add to Job message or JobService
enum JobType {
  NORMAL = 0;
  PREVIEW = 1;  // Ephemeral preview job
}
```

**Modify**: `protos/src/main/proto/interactive_session.proto` (or new proto)

```protobuf
// Streaming RPC for consuming preview data
rpc StreamPreview(StreamPreviewRequest) returns (stream StreamPreviewResponse);

message StreamPreviewRequest {
  string job_id = 1;
}

message StreamPreviewResponse {
  string key = 1;
  string value = 2;
  int64 timestamp = 3;
}
```

### Step 2: Backend - Inspector Node Type

**Modify**: `protos/src/main/proto/job.proto`

```protobuf
// Add new node type
message InspectorNode {
  string label = 1;  // Optional label for the inspector
}

// Add to PipelineNode oneof
message PipelineNode {
  string id = 1;
  oneof node_type {
    // ... existing types ...
    InspectorNode inspector = 10;
  }
}
```

**Modify**: `server/src/main/kotlin/io/typestream/compiler/node/Node.kt`

- Add `Node.Inspector` sealed class variant

**Modify**: `server/src/main/kotlin/io/typestream/scheduler/KafkaStreamsJob.kt`

- Handle Inspector node in `buildTopology()`
- Inspector writes to `${program.id}-inspect-${nodeId}` topic (similar to stdout)

### Step 3: Backend - Preview Job Service

**Modify**: `server/src/main/kotlin/io/typestream/server/JobService.kt`

- Add `createPreviewJob()` RPC handler
- Mark job as `JobType.PREVIEW`
- Add `streamPreview()` streaming RPC (or reuse GetProgramOutput pattern)
- Add `stopPreviewJob()` for cleanup

**Modify**: `server/src/main/kotlin/io/typestream/scheduler/Scheduler.kt`

- Add ability to filter preview jobs from `ps()` listing
- Add cleanup for preview jobs (auto-stop after timeout or explicit stop)

### Step 4: Frontend - Inspector Node Component

**New file**: `uiv2/src/components/graph-builder/nodes/InspectorNode.tsx`

```typescript
export function InspectorNode({ id, data }: NodeProps<InspectorNodeType>) {
  const [isStreaming, setIsStreaming] = useState(false);
  const [panelOpen, setPanelOpen] = useState(false);

  return (
    <>
      <BaseNode
        title="Inspector"
        icon={<VisibilityIcon />}
      >
        <Button
          startIcon={<PlayArrowIcon />}
          onClick={() => setPanelOpen(true)}
        >
          Preview
        </Button>
      </BaseNode>
      <Handle type="target" position={Position.Left} />

      <StreamInspectorPanel
        open={panelOpen}
        onClose={() => setPanelOpen(false)}
        nodeId={id}
      />
    </>
  );
}
```

**Modify**: `uiv2/src/components/graph-builder/nodes/index.ts`

- Add `InspectorNode` to node types registry
- Add `InspectorNodeData` interface

**Modify**: `uiv2/src/components/graph-builder/NodePalette.tsx`

- Add "Inspector" to the palette (draggable)

**Modify**: `uiv2/src/utils/graphSerializer.ts`

- Handle serialization of Inspector nodes to proto

### Step 5: Frontend - Preview Hook & Panel

**New file**: `uiv2/src/hooks/usePreviewJob.ts`

```typescript
export function usePreviewJob() {
  const [jobId, setJobId] = useState<string | null>(null);
  const [messages, setMessages] = useState<PreviewMessage[]>([]);
  const [isStreaming, setIsStreaming] = useState(false);

  const startPreview = async (graph: PipelineGraph, inspectorNodeId: string) => {
    // 1. Call createPreviewJob RPC
    // 2. Get back jobId and inspect topic
    // 3. Start streaming from streamPreview RPC
    // 4. Populate messages array
  };

  const stopPreview = async () => {
    // Stop streaming, call stopPreviewJob RPC
  };

  return { messages, isStreaming, startPreview, stopPreview };
}
```

**New file**: `uiv2/src/components/StreamInspectorPanel.tsx`

- MUI Drawer (right side, ~600px width)
- Uses `usePreviewJob` hook
- Shows streaming messages in a table (Key | Value | Timestamp)
- Close button stops the preview job
- Auto-scrolls, keeps last 100 messages

### Step 6: Frontend - Wire Up GraphBuilder

**Modify**: `uiv2/src/components/graph-builder/GraphBuilder.tsx`

- Pass current graph state to InspectorNode for preview
- Handle preview job lifecycle (stop on unmount)

---

## Critical Files

| Component | Path |
|-----------|------|
| Job proto | `protos/src/main/proto/job.proto` |
| Job service | `server/src/main/kotlin/io/typestream/server/JobService.kt` |
| Scheduler | `server/src/main/kotlin/io/typestream/scheduler/Scheduler.kt` |
| KafkaStreamsJob | `server/src/main/kotlin/io/typestream/scheduler/KafkaStreamsJob.kt` |
| Node types | `server/src/main/kotlin/io/typestream/compiler/node/Node.kt` |
| Inspector node | `uiv2/src/components/graph-builder/nodes/InspectorNode.tsx` (new) |
| Node palette | `uiv2/src/components/graph-builder/NodePalette.tsx` |
| Node registry | `uiv2/src/components/graph-builder/nodes/index.ts` |
| Graph serializer | `uiv2/src/utils/graphSerializer.ts` |
| Preview hook | `uiv2/src/hooks/usePreviewJob.ts` (new) |
| Inspector panel | `uiv2/src/components/StreamInspectorPanel.tsx` (new) |

---

## Future Enhancements

- Filter preview jobs from job list UI
- Auto-cleanup preview jobs after N minutes
- Multiple inspector nodes in same graph
- Preview for source nodes without running job (direct topic consumption)

---

## Implementation Order

1. **Proto changes** - Add InspectorNode type, preview job messages
2. **Backend Inspector node** - Handle in topology building, write to inspect topic
3. **Backend preview job** - CreatePreviewJob RPC, streaming RPC, cleanup
4. **Frontend InspectorNode** - Component with eye/preview button
5. **Frontend preview hook + panel** - Streaming UI
6. **Integration** - Wire up GraphBuilder, test end-to-end