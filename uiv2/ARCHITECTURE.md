# TypeStream UI v2 Architecture

## Overview

The UI is a React-based single-page application that provides a visual interface for building and managing TypeStream data pipelines. Users can create streaming jobs using a drag-and-drop graph builder or monitor existing jobs through a dashboard.

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
│   ├── services/           # Transport configuration
│   ├── providers/          # React providers (Query, Transport)
│   ├── hooks/              # Custom hooks for gRPC calls
│   ├── pages/              # Route-level components
│   ├── components/         # Reusable UI components
│   │   ├── layout/         # App shell (sidebar, header)
│   │   └── graph-builder/  # Visual pipeline editor
│   └── utils/              # Helpers (graph serialization)
├── buf.yaml                # Proto source configuration
└── buf.gen.yaml            # Code generation config
```

## Key Features

### Jobs Dashboard (`/jobs`)
- Lists all running/completed jobs with real-time status updates via server streaming
- Live/Disconnected connection indicator
- Status chips: Running (green), Starting/Stopping (yellow), Failed (red)
- Navigation to job details and graph builder

### Graph Builder (`/jobs/new`)
- Drag-and-drop node palette (Kafka Source, Kafka Sink)
- React Flow canvas for connecting nodes
- Topic selection dropdown for sources (fetched from filesystem)
- Submits pipeline graph to server via gRPC

### Job Details (`/jobs/:id`)
- Displays job metadata (ID, status, start time)
- Visualizes the pipeline graph

## Backend Communication

The UI communicates with the TypeStream server via gRPC using the Connect Protocol:

| Service | Methods | Purpose |
|---------|---------|---------|
| JobService | ListJobs, WatchJobs, CreateJobFromGraph | Job management |
| FileSystemService | Ls | Browse topics for dropdown |
| InteractiveSessionService | RunProgram, GetProgramOutput | Interactive mode |

### Real-time Job Updates

The `useWatchJobs` hook establishes a server-streaming connection for real-time job state updates. When job state changes on the server (STARTING → RUNNING → STOPPED), updates are pushed immediately via gRPC streaming.

To maintain consistency across components, `useWatchJobs` syncs stream updates to React Query's cache. This allows `useListJobs` (used by `JobDetailPage`) to see the same real-time data without needing its own stream connection.

```
Server stream → useWatchJobs → setJobs(local state)
                            → queryClient.setQueryData (React Query cache)
                                         ↓
                            useListJobs reads from cache
```

## Data Flow

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
| `src/hooks/useWatchJobs.ts` | Real-time job streaming + React Query sync |
| `src/hooks/useListJobs.ts` | Query-based job fetching |
| `src/hooks/useCreateJob.ts` | Job creation mutation |
| `src/components/graph-builder/GraphBuilder.tsx` | Main graph editor |
| `src/utils/graphSerializer.ts` | React Flow → Proto conversion |
