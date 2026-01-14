# TypeStream Demo Vision Plan

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

### Phase 1: Debezium + Sample Data (2-3 days)

**Goal**: CDC data flowing through TypeStream

**Tasks**:
1. Add Debezium Connect to docker-compose
2. Add Postgres with sample schema
3. Create connector registration script
4. Seed sample data (users, orders)

**Files**:
- `cli/pkg/compose/templates/docker-compose.yml`
- `scripts/dev/seed-debezium.sh` (new)
- `scripts/dev/init-postgres.sql` (new)

**Success Criteria**:
- `./typestream local dev start` brings up Debezium + Postgres
- Topics `dbserver.public.users` and `dbserver.public.orders` visible
- Schema auto-detected from Schema Registry

---

### Phase 2: Polish Graph Builder UI (1 week)

**Goal**: Smooth visual pipeline building experience

**Tasks**:
1. Topic browser panel
   - Browse `/dev/kafka/` visually
   - Preview sample messages
   - Drag to create source nodes

2. Improved node configuration
   - Topic dropdown populated from filesystem
   - Schema preview on selection
   - Field picker for filter/map expressions

3. Visual feedback
   - Schema flows through connections
   - Validation errors inline
   - Clear running/stopped states

4. Job management
   - Prominent "Run Pipeline" button
   - Job status in UI
   - Stop/restart controls

**Files**:
- `uiv2/src/components/graph-builder/GraphBuilder.tsx`
- `uiv2/src/components/graph-builder/nodes/*.tsx`
- `uiv2/src/components/TopicBrowser.tsx` (new)
- `uiv2/src/components/JobStatusPanel.tsx` (new)

**Success Criteria**:
- Can build filter → group → count pipeline entirely in UI
- No CLI needed for basic demo flow
- Clear visual feedback at each step

---

### Phase 3: State Query REST API (1 week)

**Goal**: Query KTable state stores via REST

**Based on**: `docs/docs/designs/interactive-query-rest-api.md`

**Tasks**:
1. Proto definitions
   ```protobuf
   service StateQueryService {
     rpc GetState(GetStateRequest) returns (GetStateResponse);
     rpc ListStores(ListStoresRequest) returns (ListStoresResponse);
   }
   ```

2. StateStoreRegistry
   - Track job → state store mappings
   - Register on job start, unregister on stop

3. Modify KafkaStreamsJob
   - Register materialized stores with registry
   - Expose store for querying

4. StateQueryService implementation
   - Route queries to correct job
   - Support key lookup and range scans

5. REST endpoint
   - `GET /api/v1/jobs/{job-id}/state/{store-name}?key={key}`
   - JSON response

6. UI integration
   - "Query State" panel in job detail view
   - Show current values

**Files**:
- `protos/src/main/proto/state_query.proto` (new)
- `server/src/main/kotlin/io/typestream/scheduler/StateStoreRegistry.kt` (new)
- `server/src/main/kotlin/io/typestream/server/StateQueryService.kt` (new)
- `server/src/main/kotlin/io/typestream/scheduler/KafkaStreamsJob.kt`
- `uiv2/src/components/StateQueryPanel.tsx` (new)

**Success Criteria**:
- Can query KTable state via curl
- Response includes current aggregated values
- Updates visible within 1 second of source change

---

### Phase 4: Demo Polish (2-3 days)

**Goal**: Smooth end-to-end demo experience

**Tasks**:
1. One-command startup
   - Everything starts with `./typestream local dev start`
   - Connector auto-registered
   - Sample data seeded

2. Pre-built example pipelines
   - "Order Count by Customer" - loadable from UI
   - "High Value Orders" - filter example

3. Demo script documentation
   - Step-by-step walkthrough
   - Talking points for each step

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

### Sample seed data
```sql
-- Users
INSERT INTO users (email, name, tier) VALUES
    ('alice@example.com', 'Alice Smith', 'premium'),
    ('bob@example.com', 'Bob Jones', 'standard'),
    ('carol@example.com', 'Carol White', 'premium');

-- Orders (mix of statuses)
INSERT INTO orders (user_id, total, status) VALUES
    (1, 99.99, 'completed'),
    (1, 149.99, 'completed'),
    (2, 49.99, 'pending'),
    (3, 299.99, 'completed'),
    (2, 79.99, 'completed');
```

---

## Out of Scope (Future Phases)

These features from the comprehensive capabilities document are deferred:

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
