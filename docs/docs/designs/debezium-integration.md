# Debezium Integration Plan for TypeStream

## Goal
Implement end-to-end Debezium CDC (Change Data Capture) functionality that allows users to:
1. Configure connections to databases (starting with Postgres) via UI
2. Select tables to stream from via the UI (after connector creates topics)
3. Apply TypeStream transformations
4. Sink to another database via JDBC sink connector

## Key Decisions (Confirmed)
- **MVP Scope**: Basic UI config for Postgres connections
- **Table Discovery**: After-connect discovery (topics appear once connector runs)
- **Sink Support**: Include JDBC sink for full end-to-end demo
- **Docker Image**: `debezium/connect` (smaller, CDC-focused)

## Current State
- TypeStream has Redpanda (Kafka-compatible) + Schema Registry
- Visual graph builder with Kafka source/sink nodes
- Custom demo-data connectors (NOT Kafka Connect)
- Filesystem abstraction at `/dev/kafka/local/topics`

---

## High-Level Architecture

```
┌─────────────────────────────────────────────────────────────────────────┐
│                           Docker Compose                                │
│                                                                         │
│  ┌──────────┐    ┌──────────────┐    ┌──────────┐    ┌──────────────┐  │
│  │ Postgres │───►│ Kafka Connect│───►│ Redpanda │◄───│   TypeStream │  │
│  │  (CDC    │    │  + Debezium  │    │ (Kafka)  │    │    Server    │  │
│  │  source) │    │              │    │          │    │              │  │
│  └──────────┘    └──────────────┘    └──────────┘    └──────────────┘  │
│                         │                  │                  │         │
│                         │                  ▼                  │         │
│                         │           ┌──────────┐              │         │
│                         └──────────►│ Schema   │◄─────────────┘         │
│                                     │ Registry │                        │
│                                     └──────────┘                        │
└─────────────────────────────────────────────────────────────────────────┘
```

---

## Phase 1: Infrastructure (Docker Compose)

### 1.1 Add Kafka Connect Container
```yaml
kafka-connect:
  image: debezium/connect:2.7  # or confluentinc/cp-kafka-connect
  ports:
    - "8083:8083"  # REST API
  environment:
    BOOTSTRAP_SERVERS: redpanda:9092
    CONFIG_STORAGE_TOPIC: connect-configs
    OFFSET_STORAGE_TOPIC: connect-offsets
    STATUS_STORAGE_TOPIC: connect-status
    KEY_CONVERTER_SCHEMAS_ENABLE: "true"
    VALUE_CONVERTER_SCHEMAS_ENABLE: "true"
    CONNECT_KEY_CONVERTER: io.confluent.connect.avro.AvroConverter
    CONNECT_VALUE_CONVERTER: io.confluent.connect.avro.AvroConverter
    CONNECT_KEY_CONVERTER_SCHEMA_REGISTRY_URL: http://redpanda:8081
    CONNECT_VALUE_CONVERTER_SCHEMA_REGISTRY_URL: http://redpanda:8081
```

### 1.2 Add Postgres Container (Demo/Test)
```yaml
postgres:
  image: postgres:16
  ports:
    - "5432:5432"
  environment:
    POSTGRES_USER: typestream
    POSTGRES_PASSWORD: typestream
    POSTGRES_DB: demo
  volumes:
    - ./init.sql:/docker-entrypoint-initdb.d/init.sql
  command: ["postgres", "-c", "wal_level=logical"]  # Required for CDC
```

### 1.3 Sample Schema (init.sql)
```sql
CREATE TABLE users (
    id SERIAL PRIMARY KEY,
    email VARCHAR(255) NOT NULL,
    name VARCHAR(255),
    created_at TIMESTAMP DEFAULT NOW()
);

CREATE TABLE orders (
    id SERIAL PRIMARY KEY,
    user_id INTEGER REFERENCES users(id),
    total DECIMAL(10,2),
    status VARCHAR(50) DEFAULT 'pending',
    created_at TIMESTAMP DEFAULT NOW()
);

-- Insert sample data
INSERT INTO users (email, name) VALUES
  ('alice@example.com', 'Alice'),
  ('bob@example.com', 'Bob');
```

---

## Phase 2: Connector Management Backend

### Approach: Envoy Proxy to Kafka Connect REST API
- Add route in Envoy to proxy `/connect/*` to Kafka Connect `:8083`
- Frontend directly calls Connect REST API through Envoy
- No TypeStream server changes needed for MVP
- Kafka Connect provides: list connectors, create, delete, status, config validation

### Required Changes:
1. **envoy.yaml** / **envoy-dev.yaml**: Add route for `/connect/*` → `kafka-connect:8083`
2. **Optional helper endpoint**: TypeStream could provide a simplified "create Debezium connector" endpoint that wraps the verbose Kafka Connect config

### Kafka Connect REST API (via `/connect/*`)
```
GET  /connect/connectors                    # List all connectors
POST /connect/connectors                    # Create connector
GET  /connect/connectors/{name}             # Get connector info
GET  /connect/connectors/{name}/status      # Get connector status
DELETE /connect/connectors/{name}           # Delete connector
PUT  /connect/connectors/{name}/config      # Update config
POST /connect/connectors/{name}/restart     # Restart connector
```

---

## Phase 3: Frontend - Connector Configuration Page

### 3.1 New Page: Connectors Management (`/connectors`)

Create a dedicated page for managing Debezium connectors (separate from job creation):

**List View:**
- Table of existing connectors with status (RUNNING, PAUSED, FAILED)
- "New Connector" button

**Create Connector Form:**
- **Connector Name**: Auto-generated or custom
- **Database Type**: Dropdown (Postgres for MVP, extensible)
- **Connection Details**: Host, Port, Database, Username, Password
- **Tables Filter**: Optional regex pattern (e.g., `public.*` for all public tables)
- **Topic Prefix**: Defaults to connector name

**Form Submit Flow:**
```
User fills form → Submit button
    → Frontend builds Debezium connector config JSON
    → POST /connect/connectors
    → Show "Creating..." spinner
    → Poll /connect/connectors/{name}/status until RUNNING
    → Show success, navigate to connector detail page
```

### 3.2 After-Connect Topic Discovery

Once connector is running, Debezium creates topics like:
- `{prefix}.{schema}.{table}` (e.g., `demo-pg.public.users`)

These topics automatically appear in:
- Graph Builder → Kafka Source dropdown (existing functionality)
- Topics show with AVRO encoding (Debezium registers schemas)

**No changes needed to KafkaSourceNode** - it already lists all topics!

### 3.3 UI Files to Create
```
/uiv2/src/pages/ConnectorsPage.tsx        # List connectors
/uiv2/src/pages/ConnectorCreatePage.tsx   # Create form
/uiv2/src/pages/ConnectorDetailPage.tsx   # Connector status/config
/uiv2/src/hooks/useConnectors.ts          # Fetch/create/delete hooks
/uiv2/src/services/connectApi.ts          # Kafka Connect REST client
```

### 3.4 Navigation Update
Add "Connectors" to sidebar in `AppLayout.tsx`:
- Jobs
- **Connectors** (new)
- Topics (existing or future)

---

## Phase 4: JDBC Sink Connector

### Approach: JDBC Sink via Kafka Connect
- Use `confluentinc/kafka-connect-jdbc` sink connector
- Reads from Kafka topic, writes to JDBC database (Postgres, MySQL, etc.)
- Supports: insert, upsert, update modes
- Auto-creates tables if configured

### Docker: Add JDBC Connector Plugin
The `debezium/connect` image doesn't include JDBC sink by default. Options:
1. **Custom Dockerfile** - Extend debezium/connect, add JDBC connector JAR
2. **Plugin volume mount** - Download JAR to mounted volume on startup
3. **Use confluent image** - Has JDBC sink but larger

**Recommendation**: Custom Dockerfile extending debezium/connect

```dockerfile
FROM debezium/connect:2.7
# Add JDBC sink connector
RUN cd /kafka/connect && \
    curl -L https://packages.confluent.io/maven/io/confluent/kafka-connect-jdbc/10.7.6/kafka-connect-jdbc-10.7.6.jar -o kafka-connect-jdbc.jar && \
    curl -L https://jdbc.postgresql.org/download/postgresql-42.7.3.jar -o postgresql.jar
```

### 4.1 Frontend: New Node Type `JDBCSinkNode`

Configuration fields:
- **Database Type**: Postgres (MVP)
- **Connection**: Host, Port, Database, Username, Password
- **Table Name**: Target table to write to
- **Insert Mode**: Insert / Upsert / Update
- **Primary Key Fields**: For upsert mode

### 4.2 Job Creation Flow with Sink

```
Graph Builder:
  [Debezium Topic (source)] → [Filter/Transform] → [JDBC Sink]

On "Create Job":
  1. Create TypeStream job (Kafka Streams topology)
     - Source: Debezium topic
     - Transform: filter/map operations
     - Sink: Output topic (e.g., "transformed-users")

  2. Create JDBC Sink Connector (separate from job)
     - Reads from: "transformed-users" topic
     - Writes to: target Postgres table
```

**Key insight**: The "sink" is actually TWO things:
1. TypeStream job outputs to a Kafka topic
2. JDBC Sink connector reads from that topic and writes to DB

### 4.3 UI Files to Create
```
/uiv2/src/components/graph-builder/nodes/JDBCSinkNode.tsx
/uiv2/src/pages/SinkConnectorCreatePage.tsx   # Optional dedicated page
```

### 4.4 Auto-Create Behavior (Confirmed)

When user clicks "Create Job" with a JDBCSinkNode in the graph:
1. TypeStream creates the Kafka Streams job (source → transform → output topic)
2. TypeStream auto-creates JDBC sink connector via Kafka Connect REST API
3. Connector name: `{job-id}-jdbc-sink`
4. Connector source topic: job's output topic

**Delete Behavior**: When job is deleted, also delete associated sink connector.

---

## Extensibility Framework for Multiple Connectors

### Connector Plugin Management

**Approach 1: Pre-bundled Image**
- Build custom Debezium image with common connectors pre-installed
- Simple for demo, but less flexible

**Approach 2: Plugin Download on Startup**
- Container init script downloads plugins from Confluent Hub
- More flexible, but slower startup

**Approach 3: Volume Mount Plugins**
- Mount plugins directory from host
- User can add any connector JAR

**Recommendation for MVP:** Pre-bundled image with top connectors:
- debezium-postgres
- debezium-mysql
- debezium-mongodb
- kafka-connect-jdbc (sink)

### Frontend Abstraction

Create a connector definition schema:
```typescript
interface ConnectorDefinition {
  id: string;
  name: string;
  type: 'source' | 'sink';
  icon: string;
  configFields: ConfigField[];
  connectionTestEndpoint?: string;
}

interface ConfigField {
  name: string;
  label: string;
  type: 'text' | 'password' | 'number' | 'select' | 'multiselect';
  required: boolean;
  options?: string[];  // for select/multiselect
}
```

This allows adding new connector types without code changes - just add definition JSON.

---

## Design Decisions (Confirmed)

| Decision | Choice | Rationale |
|----------|--------|-----------|
| MVP Scope | Basic UI config | Users configure Postgres connection in UI |
| Table Discovery | After-connect | Topics appear once connector runs |
| Sink Support | Include JDBC | Full end-to-end demo |
| Docker Image | debezium/connect | Smaller, CDC-focused |
| Sink UX | Auto-create | JDBCSinkNode auto-creates connector on job creation |

## Implementation Notes

1. **Connector Config Storage**: Kafka Connect stores in internal topics (automatic)
2. **Multi-tenancy**: Single shared Connect cluster for MVP
3. **Error Handling**: Poll connector status endpoint on frontend

---

## MVP Scope (Confirmed)

### Must Have:
- [ ] Docker: Kafka Connect (debezium/connect) + custom Dockerfile for JDBC sink
- [ ] Docker: Postgres container with demo schema (users, orders)
- [ ] Envoy: Proxy `/connect/*` to Kafka Connect REST API
- [ ] UI: Connectors page to create/list Debezium source connectors
- [ ] UI: Postgres source form (host, port, db, user, pass, tables filter)
- [ ] Debezium topics appear in existing Kafka Source dropdown
- [ ] UI: JDBC Sink node in graph builder
- [ ] End-to-end demo: Postgres CDC → Transform → JDBC Sink

### Nice to Have:
- [ ] Connector status polling/refresh in UI
- [ ] Connector detail page with logs
- [ ] Multiple database type support (MySQL, MongoDB)
- [ ] Seed script to auto-configure demo connector

### Out of Scope (Future):
- Connector plugin management UI
- Custom connector development
- Schema evolution handling
- Kubernetes deployment of connectors

---

## Implementation Order

### Step 1: Infrastructure (Docker)
1. Create custom Dockerfile extending `debezium/connect:2.7` with JDBC sink plugin
2. Add `kafka-connect` service to `compose.yml`
3. Add `postgres` service with init SQL to `compose.yml`
4. Update `compose.dev.yml` if needed
5. Add route in `envoy.yaml` and `envoy-dev.yaml` for `/connect/*`

### Step 2: Manual Testing
1. Start stack with `./typestream local dev start`
2. Manually create Debezium connector via curl to `/connect/connectors`
3. Verify topics appear in `/dev/kafka/local/topics`
4. Verify schemas in Schema Registry
5. Test with existing graph builder

### Step 3: Frontend - Connectors Page
1. Create `connectApi.ts` service for Kafka Connect REST
2. Create `useConnectors.ts` hooks
3. Create `ConnectorsPage.tsx` with list view
4. Create `ConnectorCreatePage.tsx` with Postgres form
5. Add "Connectors" to sidebar navigation

### Step 4: Frontend - JDBC Sink Node
1. Create `JDBCSinkNode.tsx` component
2. Add to node registry in `nodes/index.ts`
3. Update `graphSerializer.ts` to handle sink connectors
4. Update job creation to auto-create JDBC sink connector

### Step 5: End-to-End Demo
1. Create seed script to populate Postgres with sample data
2. Document the demo flow
3. Test full pipeline: CDC → Transform → Sink

---

## Critical Files to Modify

### Docker/Infrastructure
```
cli/pkg/compose/compose.yml                    # Add kafka-connect, postgres services
cli/pkg/compose/compose.dev.yml                # Dev overlay if needed
cli/pkg/compose/envoy.yaml                     # Add /connect/* route
cli/pkg/compose/envoy-dev.yaml                 # Add /connect/* route (dev)
cli/pkg/compose/Dockerfile.kafka-connect       # NEW: Custom image with JDBC
cli/pkg/compose/postgres-init.sql              # NEW: Demo schema
```

### Frontend
```
uiv2/src/pages/ConnectorsPage.tsx              # NEW: List connectors
uiv2/src/pages/ConnectorCreatePage.tsx         # NEW: Create form
uiv2/src/hooks/useConnectors.ts                # NEW: Connect API hooks
uiv2/src/services/connectApi.ts                # NEW: REST client
uiv2/src/components/graph-builder/nodes/JDBCSinkNode.tsx  # NEW
uiv2/src/components/graph-builder/nodes/index.ts          # Add JDBCSinkNode
uiv2/src/utils/graphSerializer.ts              # Handle JDBC sink
uiv2/src/components/AppLayout.tsx              # Add Connectors nav item
uiv2/src/App.tsx                               # Add routes
```

### Optional Backend (if needed)
```
protos/src/main/proto/connector.proto          # NEW: Connector service (optional)
server/src/main/kotlin/.../ConnectorService.kt # NEW: (optional)
```

---

## Demo Flow (End State)

1. User navigates to **Connectors** page
2. Clicks **"New Connector"** → Postgres form
3. Fills: host=postgres, port=5432, db=demo, user=typestream, pass=typestream
4. Clicks **Submit** → Connector created, topics appear
5. User navigates to **Jobs** → **"Create Job"**
6. Drags **Kafka Source** → selects `demo-pg.public.users` topic
7. Adds **Filter** node → `status = 'active'`
8. Drags **JDBC Sink** → configures target table `active_users`
9. Clicks **"Create Job"**
   - TypeStream job created (topic → transform → output topic)
   - JDBC Sink connector auto-created (output topic → Postgres)
10. Data flows: Postgres CDC → Kafka → Transform → Postgres sink
