# TypeStream Demo Vision

**Status**: Draft
**Last Updated**: January 2025

---

## Demo Story

> "Build visual data pipelines on Kafka and query results in real-time—no code, no databases."

**Target Audience**: Developers and data engineers evaluating streaming platforms.

**Key Differentiator**: Visual pipeline builder + queryable state stores = no Kafka Streams boilerplate, no separate database for serving layer.

---

## Demo Flow (2-minute walkthrough)

1. **Show the data source**
   - Postgres database with `users` and `orders` tables
   - "This is a typical OLTP database"

2. **Show CDC flowing to Kafka**
   - Debezium is pre-configured, streaming changes
   - Topics appear in TypeStream: `/dev/kafka/local/topics/dbserver.public.orders`
   - "Changes are captured in real-time"

3. **Build pipeline visually**
   - Drag source node, select orders topic (schema auto-detected)
   - Add filter: `status = 'completed'`
   - Add group by: `customer_id`
   - Add count aggregation
   - "No code, just drag and connect"

4. **Run the pipeline**
   - Click "Run" → Job starts
   - Show job status (running, processing records)

5. **Query the state**
   - `curl http://localhost:8080/api/v1/jobs/{id}/state/order-counts`
   - Returns current counts per customer
   - "Real-time queryable endpoint, no database needed"

6. **Show real-time update**
   - Insert new order in Postgres
   - Query again → count updated
   - "Sub-second latency from database change to queryable result"

---

## Architecture for Demo

```
┌─────────────┐     ┌─────────────┐     ┌─────────────────────┐
│  Postgres   │────▶│  Debezium   │────▶│   Kafka (Redpanda)  │
│  (source)   │ CDC │  Connect    │     │   - dbserver.*      │
└─────────────┘     └─────────────┘     └──────────┬──────────┘
                                                   │
                                                   ▼
                                        ┌─────────────────────┐
                                        │  TypeStream Server  │
                                        │  ┌───────────────┐  │
                                        │  │ Graph Builder │  │
                                        │  │   (UI)        │  │
                                        │  └───────┬───────┘  │
                                        │          │          │
                                        │          ▼          │
                                        │  ┌───────────────┐  │
                                        │  │ Kafka Streams │  │
                                        │  │   Runtime     │  │
                                        │  └───────┬───────┘  │
                                        │          │          │
                                        │          ▼          │
                                        │  ┌───────────────┐  │
                                        │  │ State Store   │◀─┼──── REST Query
                                        │  │ (KTable)      │  │
                                        │  └───────────────┘  │
                                        └─────────────────────┘
```

**Key Point**: Debezium is pre-configured. TypeStream doesn't manage connectors—it discovers and transforms the data.

---

## Implementation Phases

### Phase 1: Debezium + Sample Data

**Goal**: CDC data flowing through TypeStream

- Add Debezium Connect to docker-compose
- Add Postgres with sample schema
- Create connector registration script
- Seed sample data (users, orders)

**Success Criteria**:
- `./typestream local dev start` brings up Debezium + Postgres
- Topics `dbserver.public.users` and `dbserver.public.orders` visible
- Schema auto-detected from Schema Registry

---

### Phase 2: Polish Graph Builder UI

**Goal**: Smooth visual pipeline building experience

- Topic browser panel (browse `/dev/kafka/` visually, preview messages, drag to create nodes)
- Improved node configuration (topic dropdown, schema preview, field picker)
- Visual feedback (schema flows through connections, validation errors, running/stopped states)
- Job management (Run Pipeline button, job status, stop/restart controls)

**Success Criteria**:
- Can build filter → group → count pipeline entirely in UI
- No CLI needed for basic demo flow
- Clear visual feedback at each step

---

### Phase 3: State Query REST API

**Goal**: Query KTable state stores via REST

Based on: `docs/docs/designs/interactive-query-rest-api.md`

- Proto definitions for StateQueryService
- StateStoreRegistry to track job → state store mappings
- Modify KafkaStreamsJob to register materialized stores
- StateQueryService implementation with key lookup and range scans
- REST endpoint: `GET /api/v1/jobs/{job-id}/state/{store-name}?key={key}`
- UI integration with "Query State" panel

**Success Criteria**:
- Can query KTable state via curl
- Response includes current aggregated values
- Updates visible within 1 second of source change

---

### Phase 4: Demo Polish

**Goal**: Smooth end-to-end demo experience

- One-command startup (everything with `./typestream local dev start`)
- Pre-built example pipelines ("Order Count by Customer", "High Value Orders")
- Demo script documentation

**Success Criteria**:
- Can run full demo in under 3 minutes
- No manual setup steps
- Compelling visual story

---

## Sample Data Schema

### users table
```sql
CREATE TABLE users (
    id SERIAL PRIMARY KEY,
    email VARCHAR(255) NOT NULL,
    name VARCHAR(255),
    tier VARCHAR(50) DEFAULT 'standard',
    created_at TIMESTAMP DEFAULT NOW()
);
```

### orders table
```sql
CREATE TABLE orders (
    id SERIAL PRIMARY KEY,
    user_id INTEGER REFERENCES users(id),
    total DECIMAL(10,2),
    status VARCHAR(50) DEFAULT 'pending',
    created_at TIMESTAMP DEFAULT NOW()
);
```

---

## Out of Scope (Future Phases)

These features are deferred for now:

- **Connector Management UI** - Debezium is pre-configured, not managed
- **AI/ML Nodes** - EmbeddingGenerator, LLMEnrichment, Classification
- **Document Processing** - S3Fetch, TextExtractor, TextChunker
- **PII Detection/Masking** - Data quality nodes
- **Named REST Endpoints** - `/dev/rest/` filesystem extension
- **Multi-Instance State Queries** - Distributed query routing
- **Pipeline Persistence** - Save/load to database
- **User/Workspace Management** - Multi-tenancy

---

## Open Questions

1. **Debezium image**: Use official `debezium/connect` or `confluentinc/cp-kafka-connect`?
2. **Schema Registry**: Currently using Confluent SR. Debezium works with it, but need to verify Avro format compatibility.
3. **State store naming**: Auto-generated from job ID, or user-specified?
4. **REST gateway**: Use grpc-gateway or standalone HTTP handler?

---

## Success Metrics

**Demo is successful if**:
- Zero CLI commands needed during demo (all visual)
- Postgres change → queryable result in < 2 seconds
- Audience understands value prop: "visual pipelines + queryable state"
- No errors or manual restarts during demo
