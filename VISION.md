# TypeStream Vision

## The Pitch

> Stop writing sync jobs, cache invalidation, and API glue. Let your database do the work.

Every time you update a row, your backend has to reindex search, bust the cache, and notify downstream services. TypeStream watches your Postgres or MySQL and keeps everything in sync automatically. Your app just writes to the database -- it doesn't need to know the rest exists.

**Target audience**: Application developers and backend engineers who are tired of writing glue code between their database and downstream services.

---

## Core Concept: Your Pipeline is Your API

Most approaches push data to a database, then you build an API on top and a cache to make it fast. TypeStream skips all of that -- your pipeline computes the result and serves it through an auto-generated endpoint. No database. No cache. No API server to maintain.

---

## Architecture

```
┌──────────────┐         ┌──────────────────────────────────────────┐         ┌──────────────────┐
│              │         │  TypeStream                              │         │                  │
│ Your Database│────────▶│                                          │────────▶│ Your Destinations│
│ Postgres     │         │  ┌────────────────────────────────────┐  │         │ Postgres, ES,    │
│ MySQL        │         │  │ Visual Pipeline Builder            │  │         │ Weaviate, ...    │
│              │         │  │ -- you work here                   │  │         │                  │
│              │         │  └────────────────────────────────────┘  │         └──────────────────┘
│              │         │                                          │
│              │         │  Debezium ──▶ Kafka ──▶ Kafka Streams ──▶ Kafka Connect │
│              │         │  (CDC)       (events)   (transforms)      (sinks)       │
│              │         └──────────────────────────────────────────┘
```

Under the hood, TypeStream uses the industry-standard streaming stack -- Debezium, Kafka, and Kafka Connect -- so you get battle-tested infrastructure without having to operate it. You connect your database, build pipelines visually, and deploy.

---

## Anchor Use Cases

### 1. Keep your search index in sync -- automatically

**The problem**: Your product data lives in Postgres. Your search lives in Elasticsearch. Every API route that updates a product also has to call the search reindex. A new engineer adds an update endpoint and forgets the reindex call -- now search is stale and nobody notices for a week.

**The pipeline**:
```
Postgres CDC ──▶ Text Extract ──▶ Elasticsearch
(Products)       (Pull content)    (Always in sync)
```

A row changes in Postgres -- from any source, any route, any bulk import. TypeStream picks it up, extracts the searchable content, and updates Elasticsearch within seconds.

### 2. Add semantic search -- one more node

**The pipeline**:
```
                 ┌──▶ Text Extract ──▶ Elasticsearch (keyword search)
Postgres CDC ──▶─┤
                 └──▶ Embedding ──▶ Weaviate (semantic search)
```

Same CDC source. Now feeding both keyword search and semantic search. Both always in sync with production. "Add search that understands what users mean" usually means a quarter of engineering work. With TypeStream, you add one node.

### 3. Live API from database changes (materialized views)

**The problem**: You need an endpoint that always returns the latest state -- the current status of a job, the most recent price, a customer's usage count. The usual answer: a Postgres table, a Redis cache, an Express route, and invalidation logic.

**The pipeline**:
```
Postgres CDC ──▶ Materialize ──▶ gRPC Endpoint
(Products)       (key: product_id)  (/products/{id})
```

The pipeline keeps the latest value per key and serves it through an auto-generated endpoint. No Postgres table. No Redis cache. No Express route. Works for job statuses, inventory counts, usage metrics -- anything where you'd normally build a database + cache + API stack.

### 4. Quota tracking in real time

```
API Events ──▶ Windowed Count ──▶ gRPC Endpoint
(Kafka topic)   (60s window)       (/quota/{customer})
```

Count API calls per customer in a sliding window. No rate-limiting middleware. No Redis counters. No INCR/EXPIRE logic.

### 5. Live visits by country

```
Page Views ──▶ GeoIP ──▶ Count ──▶ gRPC Endpoint
(Kafka topic)   (ip → country)  (by country)   (/visits)
```

Real-time analytics backend without a time-series database, a cron job, or application code.

### 6. Perfect database replicas

```
Postgres CDC ──▶ Filter ──▶ ClickHouse
(CDC stream)     (status = active)  (Analytics)
```

Replicate to 200+ destinations. Filter and reshape in flight. No custom sync scripts. No ETL jobs. Millisecond latency.

### 7. AI classification in-flight

```
Tickets ──▶ OpenAI ──▶ Warehouse
(CDC stream)  (Classify urgency)  (Pre-tagged)
```

Classify every row with an OpenAI prompt as it flows through. Auto-tag support tickets, classify leads, categorize content, extract entities. No background workers, no batch jobs, no queue infrastructure.

---

## Available Nodes

### Sources
- **Kafka Topic** -- Connect to any Kafka topic directly. Read typed events from existing streams.
- **Database Table** -- Connect to Postgres or MySQL via CDC. Unwraps Debezium change events into clean row data.

### Transforms
- **Filter** -- Pass through only events matching a condition on any string key.
- **OpenAI Transformer** -- Classify, summarize, score, or extract with a prompt. Attach any OpenAI model to your stream.
- **Embedding Generator** -- Generate vector embeddings for any text field. Feed downstream vector databases.
- **Text Extractor** -- Pull searchable text content from document fields. Prepare data for search indexing.
- **GeoIP Node** -- Convert IP addresses to country, city, and region. Every event arrives geo-tagged.
- **Count / WindowedCount / Group** -- Compute real-time aggregations: events per minute, rolling counts, grouped metrics.
- **Materialized View** -- Auto-generates a gRPC endpoint from windowed aggregations. Query your streaming data directly.

### Sinks
- **Database Sink** -- Write to Postgres, MySQL, Elasticsearch, Weaviate, and 200+ other destinations via Kafka Connect.
- **Kafka Topic Sink** -- Publish transformed events back to a Kafka topic for downstream consumers.

---

## Production Qualities

- **Backpressure and retries** -- OpenAI returns a 429. Elasticsearch is temporarily down. TypeStream handles rate limiting, retries, and queueing automatically.
- **Pipelines are code** -- Version control them. Review changes in pull requests. Deploy through CI/CD. No clicking through a UI in production.
- **Observability included** -- See what's flowing, what's stuck, and why. Metrics, logs, and traces built in.

---

## Deployment

- **Self-hosted**: Deploy with `docker compose up` or Helm chart. Your data never leaves your environment.
- **Source available (BSL)**: Inspect every line of code. Converts to full open source after each release. No vendor lock-in.

---

## Success Metrics

The vision is successful when:
- A developer can go from "I need to keep Elasticsearch in sync with Postgres" to a running pipeline in under 5 minutes
- Adding a new downstream destination (e.g., semantic search) is adding one node, not a quarter of engineering work
- Zero glue code in the application -- the app writes to the database and doesn't know the rest exists
- Pipelines survive destination outages without data loss (backpressure + retries)
