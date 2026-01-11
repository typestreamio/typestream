# **TypeStream Agent Guide**

# Big Idea

**TypeStream** is a streaming data platform acting as a remote interpreter and orchestrator for Kafka Streams. It abstracts Kafka topics into a UNIX-like filesystem (/dev/kafka/...) and executes pipe-based commands (cat topic | grep 42).

Basically we can build a graph of multiple data streams that go together, that are all typed from the source (topic, debezium table etc), the transformations into the sink.

# Developing

Start the docker containers: cd cli && ./typestream local dev start
Then start the server ./scripts/dev/server.sh

You have the Puppeteer MCP available to browse the UI at http://localhost:5173

You can also use grpcurl to hit the server directly to query it

You can bootstrap topics and test data with ./scripts/dev/seed.sh

You can use `fd` instead of `find` if you want. `ripgrep` using `rg` is avaiable too.
