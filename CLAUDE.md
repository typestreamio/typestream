# **TypeStream Agent Guide**

# Big Idea

**TypeStream** lets application developers stop writing sync jobs, cache invalidation, and API glue. Your app writes to Postgres or MySQL -- TypeStream watches for changes and keeps everything else in sync automatically: search indexes, caches, vector databases, analytics warehouses, and more.

You draw pipelines visually, TypeStream handles the plumbing. Under the hood it uses Debezium (CDC), Kafka (events), Kafka Streams (transforms), and Kafka Connect (sinks). Your pipeline is also your API -- it computes results and serves them through auto-generated endpoints. No separate database, cache, or API server to maintain.

# Architecture

See **[ARCHITECTURE.md](ARCHITECTURE.md)** for the system-wide architecture, pipeline lifecycle, node reference, and data flow diagrams.

Component-level docs:
- [server/ARCHITECTURE.md](server/ARCHITECTURE.md) - Kotlin server internals (compiler, scheduler, gRPC services)
- [uiv2/ARCHITECTURE.md](uiv2/ARCHITECTURE.md) - React UI (graph builder, job dashboard)
- [cli/ARCHITECTURE.md](cli/ARCHITECTURE.md) - Go CLI (local dev, pipeline-as-code)
- [protos/ARCHITECTURE.md](protos/ARCHITECTURE.md) - Protocol Buffer API contracts
- [connectors/demo-data/ARCHITECTURE.md](connectors/demo-data/ARCHITECTURE.md) - Demo data generators

Other docs:
- [website/docs/video-encoding.md](website/docs/video-encoding.md) - How to record, encode, and embed demo videos on the landing page

# Developing

Start the docker containers: cd cli && ./typestream local dev start
Then start the server ./scripts/dev/server.sh

You have the Puppeteer MCP available to browse the UI at http://localhost:5173

You can also use grpcurl to hit the server directly to query it

You can bootstrap topics and test data with ./scripts/dev/seed.sh

You can use `fd` instead of `find` if you want. `ripgrep` using `rg` is avaiable too.

# Adding New Nodes

When adding new node types to the server:

1. Implement the node in `server/src/main/kotlin/io/typestream/compiler/node/Node.kt`
2. Add `inferOutputSchema()` for schema propagation
3. **Add integration tests** - see [server/ARCHITECTURE.md](server/ARCHITECTURE.md#integration-tests) for patterns:
   - Schema propagation test in `GraphCompilerTest.kt`
   - Graph compilation test with `StreamSource → YourNode → Sink`
   - Message flow test in `PreviewJobIntegrationTest.kt` if the node transforms data

# Vision

See [VISION.md](./VISION.md) for the demo vision plan. This document describes:
- The target demo story and flow
- Architecture for the demo (Postgres → Debezium → Kafka → TypeStream)
- Implementation phases and success criteria
- Sample data schemas

Use this as a reference point for how features should be built to support the core demo narrative: "Build visual data pipelines on Kafka and query results in real-time—no code, no databases."

# Git Workflow

See [.claude/rules](.claude/rules) for the branching policy. TL;DR: branches + PRs for all major work, direct commits to main only for quick fixes.
