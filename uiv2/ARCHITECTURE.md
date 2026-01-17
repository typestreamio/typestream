# TypeStream UI v2 Architecture

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
│   ├── providers/          # React providers (Query, Transport)
│   ├── hooks/              # Custom hooks for gRPC calls + connections
│   ├── pages/              # Route-level components
│   │   ├── JobsPage.tsx
│   │   ├── JobDetailPage.tsx
│   │   ├── GraphBuilderPage.tsx
│   │   ├── ConnectionsPage.tsx      # Database connection management
│   │   └── ConnectionCreatePage.tsx
│   ├── components/         # Reusable UI components
│   │   ├── layout/         # App shell (sidebar, header)
│   │   └── graph-builder/  # Visual pipeline editor
│   │       ├── nodes/      # Node components (KafkaSource, DbSink, etc.)
│   │       ├── GraphBuilder.tsx
│   │       └── NodePalette.tsx  # Draggable node list
│   └── utils/              # Helpers (graph serialization)
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
  - Sources: Kafka Source
  - Transforms: GeoIP, Text Extractor, Embedding Generator, OpenAI Transformer
  - Sinks: Kafka Sink, Inspector, Materialized View
  - Database Sinks: Dynamically populated from Connections page
- React Flow canvas for connecting nodes
- Real-time schema inference as you build
- Submits pipeline graph to server via gRPC

### Job Details (`/jobs/:id`)
- Displays job metadata (ID, status, start time)
- Visualizes the pipeline graph

## Backend Communication

The UI communicates with multiple backends:

| Backend | Protocol | Purpose |
|---------|----------|---------|
| TypeStream Server | gRPC (Connect) | Job management, schema inference, connection monitoring |
| Kafka Connect | REST (via Envoy) | JDBC sink connector creation |

### gRPC Services

| Service | Methods | Purpose |
|---------|---------|---------|
| JobService | ListJobs, CreateJobFromGraph, InferGraphSchemas | Job management |
| FileSystemService | Ls | Browse topics for dropdown |
| ConnectionService | GetConnectionStatuses, RegisterConnection, TestConnection | Database connection management |
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
┌─────────────────────────────────────────────────────────────────────┐
│ 1. Server auto-registers dev-postgres on startup (or user creates)  │
│    → ConnectionService monitors health via JDBC                     │
└─────────────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────────────┐
│ 2. Connected DBs appear in Graph Builder palette under "Database    │
│    Sinks" → NodePalette fetches via gRPC (useConnections hook)     │
└─────────────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────────────┐
│ 3. User drags connection to canvas, configures table name           │
│    → DbSinkNode stores full connection config + tableName           │
└─────────────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────────────┐
│ 4. On "Create Job":                                                 │
│    a. graphSerializer uses connection config from node data         │
│    b. Creates intermediate topic for TypeStream to write to         │
│    c. Submits job to TypeStream server                             │
│    d. Creates Kafka Connect JDBC Sink connector via REST API        │
│       (connector reads from intermediate topic, writes to DB)       │
└─────────────────────────────────────────────────────────────────────┘
```

## Node Types

| Node | Type | Description |
|------|------|-------------|
| Kafka Source | `kafkaSource` | Read from a Kafka topic |
| Kafka Sink | `kafkaSink` | Write to a new Kafka topic |
| GeoIP | `geoIp` | Enrich with country code from IP |
| Inspector | `inspector` | Debug sink (view data in UI) |
| Materialized View | `materializedView` | Aggregation (count/latest by key) |
| JDBC Sink (legacy) | `jdbcSink` | Inline DB config (deprecated) |
| Database Sink | `dbSink` | References a Connection profile |
| Text Extractor | `textExtractor` | Extract text from file path field |
| Embedding Generator | `embeddingGenerator` | Generate embeddings via OpenAI |
| OpenAI Transformer | `openAiTransformer` | LLM enrichment via OpenAI |

## Code Generation

Proto files in `../protos/` are compiled to TypeScript:
- `*_pb.ts` - Message types
- `*_connect.ts` - Service definitions
- `*_connectquery.ts` - React Query hooks

Run `buf generate` to regenerate after proto changes.

## Key Files

| File | Purpose |
|------|---------|
| `src/App.tsx` | Router setup |
| `src/providers/QueryProvider.tsx` | Root provider chain |
| `src/services/transport.ts` | gRPC transport config (localhost:8080) |
| `src/services/connectApi.ts` | Kafka Connect REST API client |
| `src/hooks/useCreateJob.ts` | Job creation mutation |
| `src/hooks/useConnections.ts` | Connection hooks (gRPC to ConnectionService) |
| `src/components/graph-builder/GraphBuilder.tsx` | Main graph editor |
| `src/components/graph-builder/NodePalette.tsx` | Draggable node list with dynamic connections |
| `src/components/graph-builder/nodes/DbSinkNode.tsx` | Database sink node component |
| `src/utils/graphSerializer.ts` | React Flow → Proto conversion |
