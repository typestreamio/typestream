> Part of [TypeStream Architecture](../ARCHITECTURE.md)

# CLI Architecture

## Overview

The TypeStream CLI (`typestream`) is a Go binary that serves three roles:

1. **Local development environment** - Orchestrates Docker Compose to run infrastructure services (Redpanda, Envoy, Kafka Connect, UI) and the TypeStream server
2. **Pipeline-as-code client** - Applies, validates, plans, lists, and deletes pipeline definitions (`.typestream.json` files) via gRPC
3. **Interactive shell** - REPL that connects to the server over gRPC for running TypeStream DSL commands with syntax highlighting and tab completion

When invoked with no subcommand, the CLI starts the interactive shell. When piped input via stdin, it executes the input as a one-shot program.

## Tech Stack

- **Go 1.21** - compiled binary, cross-platform (linux/darwin/windows)
- **Cobra** - command tree and flag parsing
- **gRPC + protobuf** - all server communication (insecure transport, default `127.0.0.1:4242`)
- **Docker Compose** - local infrastructure orchestration via `docker-compose` CLI
- **Docker SDK** (`github.com/docker/docker`) - direct container management for seeding
- **readline** (`github.com/reeflective/readline`) - interactive shell with history, completion
- **chroma** (`github.com/alecthomas/chroma`) - syntax highlighting in the REPL
- **charmbracelet/log** - structured logging
- **GoReleaser** - builds and publishes to Homebrew tap (`typestreamio/homebrew-tap`)

## Directory Structure

```
cli/
  main.go                    # Entry point: stdin pipe detection -> shell.Exec or cmd.Execute
  cmd/
    root.go                  # Root command, wires subcommands, default action = shell
    apply.go                 # typestream apply [file]
    plan.go                  # typestream plan [file|dir]
    validate.go              # typestream validate [file]
    pipelines.go             # typestream pipelines list|delete
    run.go                   # typestream run [file|expr]
    mount.go                 # typestream mount [config]
    unmount.go               # typestream unmount [endpoint]
    local/
      local.go               # typestream local (parent)
      dev.go                 # typestream local dev [start|stop|clean]
      start.go               # typestream local start
      stop.go                # typestream local stop
      seed.go                # typestream local seed
      show.go                # typestream local show
    k8s/
      k8s.go                 # typestream k8s (parent)
      create.go              # typestream k8s create [--redpanda]
      seed.go                # typestream k8s seed
  pkg/
    grpc/
      client.go              # Shared gRPC client wrapper
    compose/
      runner.go              # Docker Compose runner (full stack)
      dev_runner.go          # Docker Compose runner (dev mode, dependencies only)
      docker-compose.yml     # (found at project root, located by walking up from cwd)
      envoy.yaml             # Envoy proxy config (production: server in Docker)
      envoy-dev.yaml         # Envoy proxy config (dev: server on host)
      Dockerfile.kafka-connect  # Custom Debezium + Avro converter + Weaviate sink
      register-connector.sh  # Auto-registers Postgres CDC connector on startup
      custom-entrypoint.sh   # Runs registration script in background
      postgres-init.sql      # Demo schema (users, orders, file_uploads)
      .env.example           # Environment variable template
    shell/
      shell.go               # Interactive REPL and one-shot execution
      prompt.go              # Prompt rendering (shows PWD from server env)
    k8s/
      runner.go              # kubectl apply with Go templates
      typestream.yaml.tmpl   # K8s manifests (Namespace, RBAC, Deployment, Service, ConfigMap)
      redpanda.yaml          # Redpanda StatefulSet for K8s
      seeder.yaml.tmpl       # Seeder Job manifest
    version/
      version.go             # Build version, Docker image tag logic
    filesystem_service/      # Generated protobuf + gRPC stubs
    interactive_session_service/  # Generated protobuf + gRPC stubs
    job_service/             # Generated protobuf + gRPC stubs
    pipeline_service/        # Generated protobuf + gRPC stubs
  Makefile                   # build + proto targets
  .goreleaser.yaml           # Cross-platform release config
```

## Command Tree

```
typestream                          # (no args) Start interactive shell
  |-- local                         # Manage local Docker environment
  |     |-- start                   # Start full stack (server + infra) via Docker Compose
  |     |-- stop                    # Stop full stack
  |     |-- dev                     # Start infra only (server runs on host for hot reload)
  |     |     |-- stop              # Stop dev infra
  |     |     |-- clean             # Stop + remove volumes + purge built images
  |     |-- seed                    # Pull and run seeder container
  |     |-- show                    # Print docker-compose.yml contents
  |-- k8s                           # Manage Kubernetes deployment
  |     |-- create [--redpanda]     # kubectl apply server manifests (+ optional Redpanda)
  |     |-- seed                    # kubectl apply seeder Job
  |-- run [file|expr]               # Create a streaming job from DSL source
  |-- apply [file]                  # Apply a .typestream.json pipeline definition
  |-- plan [file|dir]               # Dry-run: show what apply would change
  |-- validate [file]               # Validate a .typestream.json file without applying
  |-- pipelines                     # Pipeline management
  |     |-- list                    # List all managed pipelines (tabular output)
  |     |-- delete [name]           # Delete a managed pipeline
  |-- mount [config]                # Mount a data source endpoint
  |-- unmount [endpoint]            # Unmount a data source endpoint
```

## gRPC Client

All server communication goes through `pkg/grpc/client.go`, which wraps a single `grpc.ClientConn`.

**Connection**: Dials `TYPESTREAM_HOST:TYPESTREAM_PORT` (defaults: `127.0.0.1:4242`) with insecure credentials. Each command creates a `Client`, uses it within a context with timeout, and defers `Close()`.

**Service clients**: The `Client` struct instantiates per-call service clients from the shared connection:

| Method | gRPC Service | Used By |
|--------|-------------|---------|
| `StartSession`, `RunProgram`, `GetProgramOutput`, `CompleteProgram`, `StopSession` | `InteractiveSessionService` | Shell |
| `CreateJob` | `JobService` | `run` command |
| `Mount`, `Unmount` | `FileSystemService` | `mount`/`unmount` commands |
| `ApplyPipeline`, `ValidatePipeline`, `ListPipelines`, `DeletePipeline`, `PlanPipelines` | `PipelineService` | Pipeline-as-code commands |

## Docker Compose Orchestration

Two runners manage Docker Compose with different profiles:

### `Runner` (full stack, `typestream local start`)

- Project name: `typestream`
- Compose file: `docker-compose.yml` (located by walking up from cwd)
- Sets `TYPESTREAM_IMAGE` env var from `version.DockerImage("typestream/server")`
- Runs the TypeStream server inside Docker alongside all infrastructure

### `DevRunner` (dev mode, `typestream local dev`)

- Project name: `typestream-dev`
- Compose files: `docker-compose.yml` + `docker-compose.dev.yml` (overlay)
- Sets `TYPESTREAM_PROJECT_ROOT` env var (project root, 3 directories up from `pkg/compose/`)
- Server runs on the host machine (not in Docker) for fast Kotlin iteration
- Envoy dev config routes gRPC to `host.docker.internal:4242` instead of Docker `server:4242`

Both runners capture Docker Compose stderr and forward it through a `StdOut` channel. The command goroutine parses regex patterns to present user-friendly progress messages (building, started, healthy).

**Port conflict detection**: `typestream local start` checks if port 4242 is already bound (e.g., by a Gradle dev server) before attempting to start.

### Infrastructure Services

The Docker Compose stack includes:
- **Redpanda** - Kafka-compatible broker + Schema Registry
- **Envoy** - gRPC-Web proxy (routes `/` to TypeStream server, `/connect/` to Kafka Connect)
- **Kafka Connect** - Custom Debezium image with Avro converters and Weaviate sink connector
- **PostgreSQL** - Demo database with CDC-enabled tables
- **Kafbat UI** - Kafka topic browser (port 8088)
- **TypeStream UI** - React frontend (port 5173, dev mode)

## Pipeline-as-Code Commands

The `apply`, `plan`, and `validate` commands all share `parsePipelineFile()` (defined in `cmd/validate.go`) to parse `.typestream.json` files:

```json
{
  "name": "my-pipeline",
  "version": "1",
  "description": "optional description",
  "graph": { /* PipelineGraph protobuf as JSON */ }
}
```

**Parsing**: The top-level fields (`name`, `version`, `description`) are extracted with `encoding/json`. The `graph` field is deserialized with `protojson.Unmarshal` into a `job_service.PipelineGraph` proto message. This produces a `PipelineMetadata` + `PipelineGraph` pair sent to the server.

### Example Pipeline Files

**Filter pipeline** -- filter web visits to US traffic and write to a new topic:

```json
{
  "name": "webvisits-us",
  "version": "1",
  "description": "Filter web visits to US traffic",
  "graph": {
    "nodes": [
      {
        "id": "source-1",
        "streamSource": {
          "dataStream": { "path": "/dev/kafka/local/topics/web_visits" },
          "encoding": "AVRO"
        }
      },
      {
        "id": "filter-1",
        "filter": {
          "predicate": { "expr": ".country == \"US\"" }
        }
      },
      {
        "id": "sink-1",
        "sink": {
          "output": { "path": "/dev/kafka/local/topics/web_visits_us" }
        }
      }
    ],
    "edges": [
      { "fromId": "source-1", "toId": "filter-1" },
      { "fromId": "filter-1", "toId": "sink-1" }
    ]
  }
}
```

**Aggregation pipeline** -- count web visits by country:

```json
{
  "name": "visits-by-country",
  "version": "1",
  "description": "Count web visits grouped by country",
  "graph": {
    "nodes": [
      {
        "id": "source-1",
        "streamSource": {
          "dataStream": { "path": "/dev/kafka/local/topics/web_visits" },
          "encoding": "AVRO"
        }
      },
      {
        "id": "group-1",
        "group": { "keyMapperExpr": ".country" }
      },
      {
        "id": "count-1",
        "count": {}
      }
    ],
    "edges": [
      { "fromId": "source-1", "toId": "group-1" },
      { "fromId": "group-1", "toId": "count-1" }
    ]
  }
}
```

**Enrichment pipeline** -- enrich web visits with GeoIP data and write to a new topic:

```json
{
  "name": "webvisits-enriched",
  "version": "1",
  "description": "Enrich web visits with geolocation from IP address",
  "graph": {
    "nodes": [
      {
        "id": "source-1",
        "streamSource": {
          "dataStream": { "path": "/dev/kafka/local/topics/web_visits" },
          "encoding": "AVRO"
        }
      },
      {
        "id": "geoip-1",
        "geoIp": {
          "ipField": "ip_address",
          "outputField": "country_code"
        }
      },
      {
        "id": "sink-1",
        "sink": {
          "output": { "path": "/dev/kafka/local/topics/web_visits_enriched" },
          "encoding": "AVRO"
        }
      }
    ],
    "edges": [
      { "fromId": "source-1", "toId": "geoip-1" },
      { "fromId": "geoip-1", "toId": "sink-1" }
    ]
  }
}
```

**CDC join pipeline** -- join orders with users from a Postgres CDC source:

```json
{
  "name": "orders-with-users",
  "version": "1",
  "description": "Join orders with user data from CDC",
  "graph": {
    "nodes": [
      {
        "id": "source-1",
        "streamSource": {
          "dataStream": { "path": "/dev/kafka/local/topics/demo.public.orders" },
          "encoding": "AVRO",
          "unwrapCdc": true
        }
      },
      {
        "id": "join-1",
        "join": {
          "with": { "path": "/dev/kafka/local/topics/demo.public.users" },
          "joinType": { "byKey": true }
        }
      },
      {
        "id": "sink-1",
        "sink": {
          "output": { "path": "/dev/kafka/local/topics/orders_enriched" }
        }
      }
    ],
    "edges": [
      { "fromId": "source-1", "toId": "join-1" },
      { "fromId": "join-1", "toId": "sink-1" }
    ]
  }
}
```

### apply

Sends `ApplyPipelineRequest` and reports whether the pipeline was CREATED, UPDATED, or UNCHANGED.

### plan

Accepts a single file or a directory (scans for `*.typestream.json`). Collects all pipeline definitions into a `PlanPipelinesRequest`, sends to server, and displays a table with color-coded actions (green = create, yellow = update, dim = unchanged, red = delete).

### validate

Sends `ValidatePipelineRequest` and prints validation errors if any.

### pipelines list / delete

`list` shows a table of all managed pipelines (name, version, job ID, state, description). `delete` removes a pipeline by name.

## Proto Generation

Run `make proto` from the `cli/` directory to regenerate Go stubs from `../protos/src/main/proto/*.proto`.

**Version constraints**: The generated stubs must use `protoc-gen-go v1.31.0` and `protoc-gen-go-grpc v1.2.0`. Newer versions (e.g., v1.6.1) generate `grpc.SupportPackageIsVersion9` which is incompatible with the `google.golang.org/grpc v1.59.0` dependency.

Each proto service is generated into its own `pkg/<service>/` directory. The `pipeline.proto` imports `job.proto` (for `PipelineGraph`), so they must be generated separately to avoid duplicating job types.

```makefile
proto:
  protoc --go_out=pkg/interactive_session_service ... interactive_session.proto
  protoc --go_out=pkg/job_service ... job.proto
  protoc --go_out=pkg/filesystem_service ... filesystem.proto
  protoc --go_out=pkg/pipeline_service ... pipeline.proto
```

## Interactive Shell

When `typestream` is invoked with no subcommand, `shell.Run()` starts an interactive REPL:

1. Opens a gRPC session via `InteractiveSessionService.StartSession`
2. Creates a readline shell with syntax highlighting (chroma, bash lexer) and tab completion (server-side via `CompleteProgram`)
3. Each input line is sent via `RunProgram`; if the response has `HasMoreOutput`, streams results via `GetProgramOutput` (server-sent stream)
4. Ctrl+C sends a `kill <id>` command to cancel a running program
5. On exit, calls `StopSession` to clean up server-side resources
6. History is persisted to `~/.typestream_history`

When stdin is piped (`echo "ls /dev" | typestream`), `main.go` detects the pipe and calls `shell.Exec()` for one-shot execution instead of the REPL.

## Integration Points

```
                     ┌──────────────┐
                     │   Envoy      │ :8080
                     │  (gRPC-Web)  │
                     └──────┬───────┘
                            │
   ┌────────────────────────┼──────────────────────┐
   │                        │                      │
   │  CLI (typestream)      │   React UI           │
   │  gRPC :4242 ───────────┤   Connect :8080 ─────┤
   │                        │                      │
   └────────────────────────┼──────────────────────┘
                            │
                     ┌──────┴───────┐
                     │  TypeStream  │ :4242
                     │   Server     │
                     └──────────────┘
```

- **CLI to Server**: Direct gRPC on port 4242 (no proxy needed)
- **UI to Server**: gRPC-Web through Envoy proxy on port 8080
- **CLI to Docker**: Docker Compose CLI for infrastructure lifecycle, Docker SDK for seeding
- **CLI to Kubernetes**: kubectl CLI for K8s deployments

## Release

GoReleaser builds cross-platform binaries and publishes to `typestreamio/homebrew-tap`. Version and commit hash are injected via ldflags:

```
-X github.com/typestreamio/typestream/cli/pkg/version.Version={{.Version}}
-X github.com/typestreamio/typestream/cli/pkg/version.CommitHash={{.Commit}}
```

Docker image tags are derived from the build version, with `+` replaced by `.` for Docker tag compatibility. Beta builds use GHCR (`ghcr.io/typestreamio/`).
