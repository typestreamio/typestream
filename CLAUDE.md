# **TypeStream Agent Guide**

# Big Idea

**TypeStream** is a streaming data platform acting as a remote interpreter and orchestrator for Kafka Streams. It abstracts Kafka topics into a UNIX-like filesystem (/dev/kafka/...) and executes pipe-based commands (cat topic | grep 42).

Basically we can build a graph of multiple data streams that go together, that are all typed from the source (topic, debezium table etc), the transformations into the sink.

# Architecture Deep Dives

For detailed architecture documentation, see:

- **[server/ARCHITECTURE.md](server/ARCHITECTURE.md)** - Kotlin Server architecture including the node graph builder, job system, and compilation pipeline
- **[uiv2/ARCHITECTURE.md](uiv2/ARCHITECTURE.md)** - React UI architecture with visual graph builder and job management
- **[connectors/demo-data/ARCHITECTURE.md](connectors/demo-data/ARCHITECTURE.md)** - Demo data generators for Coinbase, Wikipedia, and synthetic web visits
- cli/ - A Go program to start and stop docker compose containers etc
- **[website/docs/video-encoding.md](website/docs/video-encoding.md)** - How to record, encode, and embed demo videos on the landing page

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
