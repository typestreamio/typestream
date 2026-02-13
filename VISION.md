# TypeStream Vision

## The Pitch

> You already run Kafka. TypeStream turns it into a real-time data platform -- without writing a single line of Java.

Most teams adopt Kafka for event streaming and stop there. The topics are flowing but nobody's doing stream processing, joins, aggregations, or enrichment because every new use case means a Kafka Streams microservice -- a JVM project, serde boilerplate, topology code, deployment pipeline, and weeks of work. So the data sits in topics and teams build batch jobs around it instead.

TypeStream compiles pipeline configs into Kafka Streams topologies. You define what you want in a config file or CLI pipe, preview changes with `typestream plan`, and deploy. It reads your Schema Registry, propagates types through every node, and runs on the Kafka cluster you already operate.

**Target audience**: Engineering teams that run Kafka but aren't getting full value from it. They have topics flowing, probably a Schema Registry, maybe CDC via Debezium -- but building stream processing pipelines is still a software project, not a config change.

---

## Core Concept: Pipelines as Config, Not Code

Every Kafka Streams application requires the same boilerplate: topology definition, serde management, state store configuration, error handling, deployment. TypeStream replaces all of that with a declarative config file that compiles down to the same Kafka Streams primitives.

```
Config file (.typestream.json)
    |
    v
typestream plan  -->  "2 pipelines to create, 1 to update"
    |
    v
typestream apply -->  compiled Kafka Streams topologies running on your cluster
```

The workflow is intentionally modeled on Terraform: define the desired state, preview the diff, apply. Pipelines are version-controlled, reviewed in PRs, deployed through CI/CD.

---

## Architecture

```
  Your Existing Infrastructure             TypeStream                     Results
 +--------------------------+     +---------------------------+     +------------------+
 |                          |     |                           |     |                  |
 | Kafka Cluster            |---->| Pipeline Engine           |---->| Enriched Topics  |
 |  (topics already flowing)|     |  (compiles config into    |     | Kafka Connect    |
 |                          |     |   Kafka Streams           |     |   Sinks (ES,     |
 | Schema Registry          |---->|   topologies)             |     |   Redis, S3,     |
 |  (TypeStream reads it)   |     |                           |     |   ClickHouse...) |
 |                          |     | CLI + Config-as-Code      |     | Materialized     |
 | Debezium / CDC           |     |  typestream plan/apply    |     |   Views          |
 |  (if you have it)        |     |                           |     |                  |
 +--------------------------+     +---------------------------+     +------------------+
```

TypeStream is not a replacement for Kafka. It sits alongside your existing cluster and adds a pipeline layer. Your topics, your schemas, your infrastructure -- TypeStream just makes it easy to build on top of what you already have.

For teams that don't have Kafka yet, TypeStream bundles everything (Kafka, Schema Registry, Kafka Connect) in a single `docker compose up`. But the primary audience already has the infrastructure -- they just need a better way to use it.

---

## Why Teams Underuse Kafka

Kafka is excellent at moving events between services. But the ecosystem has a gap: going from "I have a topic" to "I want to filter, join, enrich, and route this data" requires writing a JVM application. Specifically:

1. **Kafka Streams is powerful but requires Java/Kotlin** -- most teams don't have JVM expertise on every squad, so stream processing becomes a specialized skill
2. **Every pipeline is a microservice** -- a new repo, a new deployment, a new thing to monitor. The overhead kills small use cases.
3. **Schema management is manual** -- you have Schema Registry but every consumer has to manually wire up the right serde for the right topic
4. **No preview or dry-run** -- you deploy and hope. There's no `terraform plan` equivalent for "what will this pipeline do?"
5. **Connectors are config-heavy** -- Kafka Connect can sink to 200+ destinations but configuring connectors is its own specialization

TypeStream addresses all five. Config replaces code. One engine replaces N microservices. Schema Registry integration is automatic. `typestream plan` shows you what will change before anything runs. Sink configuration is declarative and part of the pipeline definition.

---

## Anchor Use Cases

All use cases assume you have Kafka topics flowing. Some may come from CDC (Debezium), some from application events, some from external feeds. The source doesn't matter -- TypeStream works with any Kafka topic.

### 1. Enrich and route to Elasticsearch

**The situation**: You have product data flowing through a Kafka topic. You need it searchable in Elasticsearch, but you need to extract text content first. Today this means writing a Kafka Streams app or a consumer that does the extraction and pushes to ES.

**The pipeline**:
```
product-updates --> text-extract --> elasticsearch
(Kafka topic)      (pull content)   (Kafka Connect sink)
```

One config file. No JVM code. Schema is read from Schema Registry automatically. If ES goes down, TypeStream handles backpressure and retries.

### 2. Add semantic search -- one more node

```
                  +--> text-extract --> elasticsearch (keyword search)
product-updates --+
                  +--> embedding    --> weaviate (semantic search)
```

Same source topic. Now feeding both keyword and semantic search. Both stay in sync. Adding "search that understands what users mean" is adding one node to your pipeline config, not a quarter of engineering work.

### 3. Real-time aggregations without a microservice

**The situation**: You need counts, windowed aggregations, or grouped metrics from your event stream. Today that means writing a Kafka Streams app with state stores.

```
page-views --> group(.country) --> count --> output-topic
(Kafka topic)                              (or materialized view)
```

Real-time analytics from your event stream. No state store boilerplate. No KTable management. The pipeline config handles it.

### 4. Filter and fan-out

```
                  +--> filter(region="US") --> us-events
all-events -------+
                  +--> filter(region="EU") --> eu-events
                  |
                  +--> filter(priority="high") --> alerts-topic
```

Route events to different topics based on content. Today this is either a custom consumer or a Kafka Streams app. With TypeStream it's a config file.

### 5. AI enrichment in-flight

```
support-tickets --> openai(classify urgency) --> enriched-tickets --> warehouse
(Kafka topic)      (LLM transform)              (Kafka topic)        (Kafka Connect)
```

Run every event through an LLM as it flows. Classify, summarize, extract entities, score sentiment. The enriched output lands in a new topic and/or a downstream sink. No batch jobs, no separate ML pipeline.

### 6. CDC to warehouse

```
demo.public.orders --> filter(status="completed") --> clickhouse
(Debezium CDC topic)                                 (Kafka Connect sink)
```

If you already have Debezium producing CDC topics, TypeStream can filter, transform, and route them to any of 200+ Kafka Connect destinations. The CDC topics are just Kafka topics -- TypeStream treats them the same way.

### 7. Join streams

```
orders ----+
           +--> join(by key) --> enriched-orders --> sink
users -----+
```

Join two topics by key. Today this is one of the hardest things to do in Kafka Streams (KStream-KTable joins, windowed joins, serde alignment). TypeStream handles the topology.

---

## Available Nodes

### Sources
- **Kafka Topic** -- Connect to any Kafka topic. Schema read from Schema Registry. Supports Avro, JSON.
- **CDC Topic** -- Same as Kafka Topic but with `unwrapCdc: true` to extract the `after` payload from Debezium envelope.

### Transforms
- **Filter** -- Row-level filtering on any field.
- **Map** -- Transform values with expressions.
- **Group** -- Re-key records for downstream aggregation.
- **Join** -- Join two streams by key.
- **Each** -- Side-effects (logging, debugging) without altering data flow.

### Aggregations
- **Count** -- Count records per key (compiles to KTable).
- **WindowedCount** -- Tumbling window counts.
- **ReduceLatest** -- Keep latest value per key (materialized view).

### Enrichment
- **GeoIP** -- Convert IP addresses to location data via MaxMind.
- **TextExtractor** -- Extract text from documents via Apache Tika.
- **EmbeddingGenerator** -- Generate vector embeddings via OpenAI.
- **OpenAiTransformer** -- LLM-based classification, extraction, summarization.

### Sinks
- **Kafka Topic** -- Write to any Kafka topic.
- **Kafka Connect** -- 200+ destinations: Elasticsearch, Redis, ClickHouse, Weaviate, S3, Snowflake, PostgreSQL, MySQL, and more.

---

## Schema Propagation

TypeStream reads your Schema Registry and propagates types through every node at compile time. This means:

- Source schemas are resolved automatically -- no serde configuration
- Each node declares how it transforms the schema (filter passes through, join merges, enrichment adds a field)
- Schema errors are caught before any topology runs
- The UI can show field names on every edge for visual debugging
- When an upstream schema changes, the pipeline knows

This is a genuine differentiator vs. writing Kafka Streams by hand, where schema management is entirely manual.

---

## Production Qualities

- **Plan before you deploy** -- `typestream plan` shows exactly what will be created, updated, or deleted. Like terraform for data pipelines. Review in CI, approve in a PR.
- **Backpressure and retries** -- If a sink is slow or down, TypeStream queues and retries. No data loss.
- **Pipelines are code** -- Version-controlled config files. PR reviews. CI/CD deployment. No clickops.
- **Observability** -- Metrics, logs, and traces built in. Know what's flowing, what's stuck, and why.

---

## Deployment

- **Self-hosted**: Deploy with `docker compose up` or Helm chart. Connects to your existing Kafka cluster. Your data never leaves your environment.
- **Bundled mode**: For teams without Kafka, TypeStream ships everything (Kafka, Schema Registry, Kafka Connect) in a single compose file.
- **Source available (BSL)**: Inspect every line of code. Converts to full open source after each release. No vendor lock-in.

---

## Success Metrics

The vision is successful when:
- A team with Kafka can go from "I need to filter and route this topic" to a running pipeline in under 5 minutes
- Adding a new stream processing use case is a config file change, not a new microservice
- Teams that were only using Kafka for pub/sub start doing joins, aggregations, enrichment, and AI transforms
- Nobody has to write Kafka Streams Java to get Kafka Streams capabilities
- `typestream plan` gives teams the same confidence deploying pipelines that `terraform plan` gives them deploying infrastructure
