# TypeStream examples

Self-contained demos you can run with `docker compose up`. Each is a complete story —
source of truth in Postgres, TypeStream keeping a downstream system fresh via CDC, and a
small app reading the result.

| Example | Story | Sink style |
|---------|-------|------------|
| [`weaviate-live-rag`](./weaviate-live-rag) | Continuously-fresh RAG over a help center | OOTB Kafka connector, registered by hand |
| [`qdrant-live-rag`](./qdrant-live-rag) | Same story, Qdrant | First-class `qdrantSink` node (server provisions the connector) |

## The "live RAG" template

Both RAG examples share one shape and differ only at a small **per-target seam**. Use this
as the template for a new vector-DB target (Pinecone, Milvus, pgvector, …).

### Shared, vendor-neutral core (copy as-is)

- `db/01-help-articles.sql` — Postgres schema + 10 fictional "Northwind" rows (`REPLICA IDENTITY FULL` for CDC).
- `demo/update-trial.sql`, `demo/insert-sso.sql` — the two on-camera beats (UPDATE flip, INSERT of a new topic).
- `bootstrap/bootstrap.sh` — registers the Postgres CDC connector, waits for the snapshot, calls the
  target seam, applies the pipeline, waits for docs to land. `bootstrap/Dockerfile` — tiny curl+jq+grpcurl image.
- `chatbot/server.js`, `chatbot/public/index.html` — vendor-neutral RAG endpoint + UI.
- `ui/envoy.yaml`, `ui/Caddyfile` — the TypeStream UI proxy (only the datastore route in the Caddyfile changes).
- The `redpanda`, `postgres`, `kafka-connect`, `server`, `uiv2`, `envoy`, `caddy` compose services.

### Per-target seam (rewrite for each datastore)

1. **`docker-compose.yml`** — swap the datastore service block; point the chatbot and the
   server's dev connection env at it.
2. **The sink** — choose one:
   - **Native sink node** (cleanest demo; what `qdrant-live-rag` does): the pipeline JSON uses a
     first-class sink node (e.g. `qdrantSink`) and the **server** reshapes records and provisions
     the connector on `ApplyPipeline`. The server must have `KAFKA_CONNECT_URL` set. `target.sh`
     only pre-creates the collection. Requires the datastore to be supported by the server.
   - **OOTB connector by hand** (any connector, zero server changes; what `weaviate-live-rag` does):
     the pipeline writes a flat record to a Kafka topic with `kafkaSink`, and `target.sh`'s
     `register_sink_connector` registers the connector against that topic via the Kafka Connect REST API.
3. **`chatbot/retriever.js`** — implement `retrieve(question, k) -> [{title, body}]`: embed the
   question and run a vector search. Keep the export shape so `server.js` stays untouched.

### Choosing a style

- The OOTB-connector route works for **any** connector that can field-map a flat record, with no
  server changes — ideal for quickly standing up a new target.
- The native-node route gives the cleanest pipeline/diagram and handles connectors whose record
  shape the pipeline can't express directly (Qdrant needs a nested `{id, vector, payload}` envelope,
  which the server builds via an internal reshape step). It costs a small amount of server work per
  vendor (proto + desugarer + a `Create…SinkConnector` in `ConnectionService`).

### Gotchas

- Use the **same embedding model** in the pipeline and the chatbot, and match the collection's
  vector size to it (text-embedding-3-small = 1536 dims).
- Some vector DBs require the collection/index to exist before writes (no auto-schema) — create it
  in `target.sh`.
- Deletes (removing ghost vectors) are out of scope in these demos.
