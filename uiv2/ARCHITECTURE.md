# TypeStream UI v2 Architecture

> Part of [TypeStream Architecture](../ARCHITECTURE.md)

## Overview

The UI is a React-based single-page application that provides a visual interface for building and managing TypeStream data pipelines. Users can create streaming jobs using a drag-and-drop graph builder, manage database connections for sinks, or monitor existing jobs through a dashboard.

## Tech Stack

| Layer | Technology |
|-------|------------|
| Framework | React 19 + TypeScript |
| Build | Vite |
| UI Components | Material-UI (MUI) |
| API Communication | Connect Protocol (gRPC-Web) |
| State Management | TanStack React Query |
| Routing | React Router DOM |
| Graph Editor | XYFlow React |

## Directory Structure

```
uiv2/
├── src/
│   ├── generated/          # Auto-generated from protos (DO NOT EDIT)
│   ├── services/           # Transport config + API clients
│   │   ├── transport.ts    # gRPC transport config
│   │   └── connectApi.ts   # Kafka Connect REST API client
│   ├── providers/          # React providers (Query, Transport, ServerConnection, ThroughputHistory)
│   ├── hooks/              # Custom hooks for gRPC calls + connections
│   ├── pages/              # Route-level components
│   │   ├── JobsPage.tsx
│   │   ├── JobDetailPage.tsx
│   │   ├── GraphBuilderPage.tsx
│   │   ├── ConnectionsPage.tsx           # Database connection management
│   │   ├── ConnectionCreatePage.tsx
│   │   ├── ConnectorsPage.tsx            # Kafka Connect connector management
│   │   ├── ElasticsearchConnectionsPage.tsx
│   │   └── WeaviateConnectionsPage.tsx
│   ├── components/         # Reusable UI components
│   │   ├── layout/         # App shell (sidebar, header, server status)
│   │   └── graph-builder/  # Visual pipeline editor
│   │       ├── nodes/      # Node components (13 types)
│   │       ├── edges/      # AnimatedEdge for data flow visualization
│   │       ├── GraphBuilder.tsx
│   │       ├── NodePalette.tsx
│   │       └── PipelineGraphViewer.tsx
│   └── utils/              # Helpers (graph serialization, deserialization, pipeline files)
├── buf.yaml                # Proto source configuration
└── buf.gen.yaml            # Code generation config
```

## Key Features

### Jobs Dashboard (`/jobs`)
- Lists all running/completed jobs with status indicators
- Status chips: Running (green), Starting/Stopping (yellow), Failed (red)
- Navigation to job details and graph builder

### Connections (`/connections`)
- Manage database connection profiles (PostgreSQL, MySQL)
- Connections are managed by the server with live health monitoring
- Server auto-registers `dev-postgres` on startup for development
- Connected databases appear as sink options in the graph builder

### Graph Builder (`/jobs/new`)
- **Node Palette** organized by category:
  - Sources: Kafka Source, Postgres Source (CDC)
  - Transforms: GeoIP, Text Extractor, Embedding Generator, OpenAI Transformer, Filter
  - Sinks: Kafka Sink, Inspector, Materialized View
  - Database/Vector Sinks: Dynamically populated from Connections pages (DB, Weaviate, Elasticsearch)
- React Flow canvas for connecting nodes
- Real-time schema inference as you build (via `useInferGraphSchemas` hook)
- Submits pipeline graph to server via gRPC

### Job Details (`/jobs/:id`)
- Displays job metadata (ID, status, start time)
- Visualizes the pipeline graph with `PipelineGraphViewer`
- Stream inspector panel for viewing live data
- Throughput sparkline visualization

## Node Type Mapping

The graph builder maps React Flow node types to proto `PipelineNode` types via `graphSerializer.ts`:

| React Flow Type | Proto Case | Proto Message | Role |
|----------------|------------|---------------|------|
| `kafkaSource` | `streamSource` | `StreamSourceNode` | source |
| `postgresSource` | `streamSource` | `StreamSourceNode` (unwrapCdc=true) | source |
| `kafkaSink` | `sink` | `SinkNode` | sink |
| `geoIp` | `geoIp` | `GeoIpNode` | transform |
| `textExtractor` | `textExtractor` | `TextExtractorNode` | transform |
| `embeddingGenerator` | `embeddingGenerator` | `EmbeddingGeneratorNode` | transform |
| `openAiTransformer` | `openAiTransformer` | `OpenAiTransformerNode` | transform |
| `filter` | `filter` | `FilterNode` | transform |
| `inspector` | `inspector` | `InspectorNode` | sink |
| `materializedView` | `group` + `count`/`windowedCount`/`reduceLatest` | `GroupNode` + aggregation | sink |
| `dbSink` | `sink` | `SinkNode` (+ `DbSinkConfig`) | sink |
| `weaviateSink` | `sink` | `SinkNode` (+ `WeaviateSinkConfig`) | sink |
| `elasticsearchSink` | `sink` | `SinkNode` (+ `ElasticsearchSinkConfig`) | sink |

Each node component exports a `role` constant (`'source'`, `'transform'`, or `'sink'`) that controls handle visibility:
- Sources: output handle only
- Transforms: input + output handles
- Sinks: input handle only

### Schema Validation

Nodes declare field type requirements via `nodeFieldRequirements`:

```typescript
const nodeFieldRequirements = {
  geoIp: { ipField: 'string' },
  textExtractor: { filePathField: 'string' },
  embeddingGenerator: { textField: 'string' },
  materializedView: { groupByField: 'any' },
  dbSink: { primaryKeyFields: 'any' },
};
```

The `NodeValidationState` interface provides `outputSchema`, `schemaError`, and `isInferring` state populated by the schema inference response.

## Backend Communication

The UI communicates with multiple backends:

| Backend | Protocol | Purpose |
|---------|----------|---------|
| TypeStream Server | gRPC (Connect) | Job management, schema inference, connection monitoring |
| Kafka Connect | REST (via Envoy) | JDBC/Weaviate/Elasticsearch sink connector creation |

### gRPC Services

| Service | Methods Used | Purpose |
|---------|-------------|---------|
| JobService | ListJobs, CreateJobFromGraph, InferGraphSchemas | Job management + real-time schema inference |
| FileSystemService | Ls | Browse topics for dropdowns |
| ConnectionService | GetConnectionStatuses, RegisterConnection, TestConnection | Database connection management |
| StateQueryService | GetAllValues, ListStores | State store queries for materialized views |
| InteractiveSessionService | RunProgram, GetProgramOutput | Interactive mode |

## Data Flow

### Standard Job Creation
```
User drags nodes → GraphBuilder state
     ↓
"Create Job" click
     ↓
graphSerializer.ts converts React Flow → PipelineGraph proto
     ↓
useCreateJob mutation → JobService.CreateJobFromGraph
     ↓
Server compiles graph → KafkaStreamsJob
     ↓
Navigate to job detail page
```

### Database Sink Flow
```
1. Server auto-registers dev-postgres on startup (or user creates)
   → ConnectionService monitors health via JDBC
                        ↓
2. Connected DBs appear in Graph Builder palette under "Database Sinks"
   → NodePalette fetches via gRPC (useConnections hook)
                        ↓
3. User drags connection to canvas, configures table name
   → DbSinkNode stores connectionId + tableName + insertMode
                        ↓
4. On "Create Job":
   a. graphSerializer creates intermediate topic for TypeStream to write to
   b. Creates SinkNode pointing to intermediate topic
   c. Creates DbSinkConfig with connectionId (no credentials)
   d. Submits job + sink configs to TypeStream server
   e. Server resolves credentials and creates Kafka Connect connector
```

## Code Generation

Proto files in `../protos/` are compiled to TypeScript using `@bufbuild/protobuf`:
- `*_pb.ts` - Message types with `toJson()` / `fromJson()` serialization
- `*_connect.ts` - Service definitions
- `*_connectquery.ts` - React Query hooks for TanStack Query

Run `buf generate` to regenerate after proto changes.

## Integration Points

### TypeStream Server (gRPC)
- Transport configured in `transport.ts` pointing to `localhost:8080` (dev) or Envoy proxy (prod)
- Uses Connect Protocol (gRPC-Web compatible) via `@connectrpc/connect-web`
- All queries wrapped in TanStack React Query for caching and refetch

### Kafka Connect (REST)
- `connectApi.ts` provides `createConnector()` and `getConnectorStatus()` functions
- Accessed via Envoy proxy at `/kafka-connect/` path prefix
- Used for JDBC, Weaviate, and Elasticsearch sink connectors

### Schema Registry
- Indirect access through `FileSystemService.Ls` and `JobService.InferGraphSchemas`
- Schema data flows back from server as `SchemaField[]` (name + type string)

## Key Files

| File | Purpose |
|------|---------|
| `src/App.tsx` | Router setup |
| `src/providers/QueryProvider.tsx` | Root provider chain |
| `src/providers/ServerConnectionProvider.tsx` | Server health monitoring |
| `src/services/transport.ts` | gRPC transport config |
| `src/services/connectApi.ts` | Kafka Connect REST API client |
| `src/hooks/useCreateJob.ts` | Job creation mutation |
| `src/hooks/useInferGraphSchemas.ts` | Real-time schema inference for graph builder |
| `src/hooks/useConnections.ts` | Connection hooks (gRPC to ConnectionService) |
| `src/hooks/usePreviewJob.ts` | Preview job streaming |
| `src/components/graph-builder/GraphBuilder.tsx` | Main graph editor |
| `src/components/graph-builder/NodePalette.tsx` | Draggable node list with dynamic connections |
| `src/components/graph-builder/nodes/index.ts` | Node type registry and data interfaces |
| `src/utils/graphSerializer.ts` | React Flow -> Proto conversion |
| `src/utils/graphDeserializer.ts` | Proto -> React Flow conversion (for viewing existing jobs) |
| `src/utils/pipelineFile.ts` | Pipeline file format utilities |
