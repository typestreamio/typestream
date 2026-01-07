# TypeStream UI v2 (uiv2)

Modern React 19 + TypeScript frontend for TypeStream, built with Connect protocol and React Query for seamless gRPC integration.

## Overview

Phase 5 implementation provides a complete frontend that communicates with TypeStream's gRPC services via Envoy proxy at `http://localhost:8080`.

**Architecture:**
```
Browser (React) → Envoy (HTTP/1.1 + Connect) → gRPC Server (HTTP/2) → TypeStream Services
```

## Features

✅ **Browse Kafka Topics** - List and inspect Kafka topics with schema information
✅ **Submit Text Pipelines** - Type typestream commands (e.g., `cat topics | grep Station`)
✅ **Submit Visual Pipelines** - Submit graph-based jobs via PipelineGraph proto
✅ **Real-time Job Monitoring** - Stream and display job output as it executes

## Tech Stack

- **React 19** - UI framework
- **TypeScript** - Type safety
- **Vite** - Build tool and dev server
- **Connect Protocol** - Modern gRPC-Web alternative
- **TanStack React Query** - Data fetching, caching, and state management
- **Buf** - Protocol Buffers toolchain and code generation

## Getting Started

### Prerequisites

- TypeStream backend services running (see main project README)
- Envoy proxy configured and running on port 8080
- Node.js 18+ and pnpm installed

### Installation

```bash
cd uiv2
pnpm install
```

### Proto Code Generation

The frontend uses generated TypeScript types and Connect clients from Protocol Buffer definitions:

```bash
pnpm proto
```

This runs `buf generate` on the protos in `../protos/src/main/proto/` and outputs:
- `*_pb.ts` - TypeScript types
- `*_connect.ts` - Connect clients for gRPC calls
- `*_connectquery.ts` - React Query hooks

### Development

```bash
pnpm dev
```

The app will be available at `http://localhost:5173`

### Build

```bash
pnpm build
```

## Project Structure

```
uiv2/
├── src/
│   ├── generated/          # Auto-generated from proto files (DO NOT EDIT)
│   │   ├── job_pb.ts       # Job service types
│   │   ├── filesystem_pb.ts # FileSystem service types
│   │   ├── interactive_session_pb.ts # Interactive session types
│   │   ├── *_connect.ts    # Connect clients
│   │   └── *_connectquery.ts # React Query hooks
│   │
│   ├── services/
│   │   └── transport.ts    # Connect transport configuration
│   │
│   ├── providers/
│   │   └── QueryProvider.tsx # TanStack Query + Transport wrapper
│   │
│   ├── hooks/              # Custom React hooks for gRPC calls
│   │   ├── useKafkaTopics.ts    # Browse Kafka topics
│   │   ├── useJobSubmit.ts       # Submit text pipelines
│   │   ├── useGraphJobSubmit.ts  # Submit graph pipelines
│   │   ├── useProgramOutput.ts   # Stream job output (TODO: complete)
│   │   ├── useSession.ts         # Start interactive session
│   │   └── useRunProgram.ts      # Run program in session
│   │
│   ├── components/         # UI components
│   │   ├── KafkaTopicBrowser.tsx # List Kafka topics
│   │   ├── JobSubmitter.tsx       # Text pipeline form
│   │   ├── GraphJobSubmitter.tsx  # Graph pipeline example
│   │   └── JobMonitor.tsx          # Job output display (TODO: complete)
│   │
│   └── App.tsx             # Main application component
│
├── buf.yaml              # Buf workspace configuration
├── buf.gen.yaml          # Code generation configuration
├── package.json
├── vite.config.ts
└── tsconfig.json
```

## Components

### KafkaTopicBrowser

Lists Kafka topics from `/dev/kafka/local/topics` using `FileSystemService.Ls`.

```typescript
import { useKafkaTopics } from '../hooks/useKafkaTopics';

const { data, isLoading, error } = useKafkaTopics('local');
// data.files = [{ name: 'books', type: 'FILE', schema: '...' }, ...]
```

**Features:**
- Shows topic name and type
- Displays schema information when available
- Loading and error states

### JobSubmitter

Submit text-based pipelines using the familiar TypeStream syntax.

**Example:**
```
cat /dev/kafka/local/topics/books | grep Station
```

**API:**
```typescript
const mutation = useJobSubmit();
mutation.mutate({ userId: 'local', source: 'cat topics | grep foo' });
// Returns: { success: true, jobId: 'abc123', error: '' }
```

### GraphJobSubmitter

Submit visual pipelines built from `PipelineGraph` protobuf messages.

**Example Graph:**
```typescript
const graph: PipelineGraph = {
  nodes: [
    {
      id: 'source-1',
      nodeType: {
        case: 'streamSource',
        value: {
          stream: { path: '/dev/kafka/local/topics/books' },
          encoding: 'STRING',
        },
      },
    },
    {
      id: 'filter-1',
      nodeType: {
        case: 'filter',
        value: {
          byKey: false,
          predicate: { expr: 'Station' },
        },
      },
    },
  ],
  edges: [{ fromId: 'source-1', toId: 'filter-1' }],
};
```

**API:**
```typescript
const mutation = useGraphJobSubmit();
mutation.mutate({ userId: 'local', graph });
// Returns: { success: true, jobId: 'abc123', error: '' }
```

### JobMonitor (WIP)

Monitor streaming job output in real-time.

**Planned Features:**
- Start interactive session via `StartSession`
- Run program and get program ID via `RunProgram`
- Stream output via `GetProgramOutput` (server streaming RPC)
- Display stdout/stderr as they arrive

**Current Status:** 🚧 Work in progress - needs integration with job submission components

## Available gRPC Services

| Service | RPC | Frontend Use |
|---------|-----|--------------|
| **JobService** | `CreateJob` | Submit text pipelines |
| | `CreateJobFromGraph` | Submit visual pipelines |
| **FileSystemService** | `Ls` | Browse Kafka topics |
| | `Mount` / `Unmount` | (Future) Manage Kafka clusters |
| **InteractiveSessionService** | `StartSession` | Create interactive session |
| | `RunProgram` | Execute pipeline in session |
| | `GetProgramOutput` | Stream job output |
| | `CompleteProgram` | Auto-complete pipeline source |

## Data Flow

### Text Pipeline Flow
```
User types: "cat /dev/kafka/local/topics/books | grep Station"
    ↓
JobSubmitter collects source
    ↓
useJobSubmit → JobService.CreateJob RPC
    ↓
Server compiles text → Program → Kafka Streams Topology
    ↓
Job runs and produces output
    ↓
JobMonitor displays via InteractiveSessionService
```

### Graph Pipeline Flow (Y-Interface)
```
GraphJobSubmitter has pre-built PipelineGraph
    ↓
useGraphJobSubmit → JobService.CreateJobFromGraph RPC
    ↓
Server: GraphCompiler hydrates proto → Graph<Node>
    ↓
Infer validates types and schema
    ↓
Vm.runProgram(program) → Kafka Streams Topology
    ↓
Job runs (same execution path as text)
```

## Configuration

### Transport

Located in `src/services/transport.ts`:

```typescript
export const transport = createConnectTransport({
  baseUrl: 'http://localhost:8080',
});
```

Change `baseUrl` to match your Envoy proxy address.

### Buf Configuration

**buf.yaml:** Points to proto files
```yaml
version: v2
modules:
  - path: ../protos/src/main/proto
```

**buf.gen.yaml:** Code generation settings
```yaml
version: v2
plugins:
  - remote: buf.build/bufbuild/es
    out: src/generated
    opt:
      - target=ts
  - remote: buf.build/connectrpc/es
    out: src/generated
    opt:
      - target=ts
```

## Development Notes

### Adding New gRPC Calls

1. **Create hook** in `src/hooks/`:
   ```typescript
   import { useQuery } from '@connectrpc/connect-query';
   import { myRpc } from '../generated/my_service_connect.ts';

   export function useMyRpc(param: string) {
     return useQuery(myRpc, { param });
   }
   ```

2. **Use in component:**
   ```typescript
   const { data, isLoading, error } = useMyRpc('value');
   ```

### Proto Changes

If you modify proto files in `../protos/src/main/proto/`:

```bash
cd uiv2
pnpm proto
```

Then restart Vite dev server to pick up changes.

## Troubleshooting

### CORS Issues

If you see CORS errors, verify Envoy is configured with proper CORS headers:

```yaml
http_filters:
  - name: envoy.filters.http.cors
    typed_config:
      allow_origin_string_match:
        - safe_regex:
            regex: .*
      allow_headers: "content-type,x-grpc-web,x-user-agent,connect-protocol-version"
```

### Import Errors

All generated imports should use `.ts` extension:

```typescript
// ✅ Correct
import { createJob } from '../generated/job_connect.ts';

// ❌ Wrong
import { createJob } from '../generated/job_connect.js';
```

### Connect vs gRPC-Web

- Connect is the modern protocol (HTTP/1.1 + JSON or protobuf)
- gRPC-Web is older (requires special headers)
- Envoy currently uses grpc-web filter with Connect-compatible CORS settings
- Both work, but Connect is recommended for new projects

## Testing

Run the full stack:

```bash
# Terminal 1: Start TypeStream services
docker-compose up

# Terminal 2: Start frontend
cd uiv2
pnpm dev
```

Then test:
1. **Browse topics**: Scroll to "Kafka Topics" section
2. **Submit text pipeline**: Enter source, click "Run Pipeline"
3. **Submit graph pipeline**: Click "Run Visual Pipeline"
4. **Monitor output**: (TODO: Complete this feature)

## Next Steps (Phase 6+)

- **Rete.js Integration**: Visual drag-and-drop editor for PipelineGraph construction
- **Job History**: List and manage previously submitted jobs
- **Schema Viewer**: Browse and inspect Kafka message schemas
- **Connectors UI**: Configure Debezium/Kafka Connect sources
- **Real-time Dashboards**: Live metrics and pipeline monitoring

## References

- [Connect Protocol Documentation](https://connectrpc.com/docs)
- [Buf Build](https://buf.build/docs)
- [TanStack React Query](https://tanstack.com/query/latest)
- [TypeStream Architecture](https://github.com/typestreamio/typestream)

## License

Same as TypeStream main project

