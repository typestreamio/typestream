# Live RAG: Postgres → TypeStream → Qdrant

A self-contained demo of **continuously-fresh RAG**. A support chatbot answers from a
Qdrant collection of help-center docs. The source of truth is Postgres — and the moment a
row changes, TypeStream re-embeds it and the bot's answer changes seconds later. No
reindex job, no consumer code, no Kafka to operate.

> Data is fictional ("Northwind"). Nothing here is real.

![The pipeline in the TypeStream UI](pipeline-graph.png)

## What's inside

```
Postgres(help_articles) --Debezium CDC--> Kafka --> TypeStream pipeline
   (kafkaSource unwrapCdc -> embeddingGenerator -> qdrantSink: help_articles)
   --> qdrant-kafka sink connector --> Qdrant(help_articles)
                                          ^ vector search
   chatbot (Node+Express) ----------------/   embed question -> top-3 -> grounded answer
```

The pipeline ends in a native `qdrantSink` node: TypeStream reshapes each record into
the envelope the [qdrant-kafka](https://github.com/qdrant/qdrant-kafka) connector expects
(`{collection_name, id, vector, payload}`) and registers the connector itself — the
pipeline file is the whole configuration.

The chatbot queries Qdrant directly; TypeStream's only job is keeping Qdrant fresh.

## Run it

```bash
cp .env.example .env          # add your OPENAI_API_KEY
docker compose up             # wait for the `bootstrap` container to print "Demo is live."
```

Then open **http://localhost:8000**.

The `bootstrap` container runs once: it registers the CDC connector, creates the Qdrant
collection, and applies the TypeStream pipeline (the server creates the Qdrant sink
connector as part of the apply), then exits. After that the pipeline is persisted in
Kafka and auto-recovers on every restart.

## The two demo moves

Dry-run all three questions before recording — answers are deterministic (temperature 0),
so only freshness changes on camera.

**1. UPDATE — a clean number flip**

Ask: *"How long is the free trial?"* → **"14 days."**

```bash
docker compose exec -T postgres psql -U typestream -d demo < demo/update-trial.sql
```

Ask the same question again → **"30 days."**

**2. INSERT — a topic that didn't exist**

Ask: *"Do you support single sign-on / Okta?"* → **"I don't have that information."**

```bash
docker compose exec -T postgres psql -U typestream -d demo < demo/insert-sso.sql
```

Ask again seconds later → the bot answers from the brand-new SSO doc.

## Retargeting to another datastore

The reusable core (Postgres, CDC, redpanda, TypeStream server, `bootstrap.sh`, the chatbot
shell) is vendor-neutral. Only two files encode "Qdrant":

| File | What changes |
|------|--------------|
| `bootstrap/target.sh` | `create_collection` + connector/doc-count readiness checks |
| `chatbot/retriever.js` | how a question becomes documents (`retrieve(q, k) -> [{title, body}]`) |
| `docker-compose.yml` | swap the `qdrant` service block + the chatbot's `QDRANT_URL` |
| `pipeline/*.json` | swap the sink node (`qdrantSink` → your destination's sink) |

## Notes & limits

- Requires `OPENAI_API_KEY` (server embeds docs; chatbot embeds queries + generates answers).
- Embedding model is pinned to `text-embedding-3-small` on both sides — keep them in sync.
  The Qdrant collection is created with the matching vector size (1536, Cosine).
- **Deletes are out of scope** here (insert + update only). The upstream CDC unwrap drops
  delete events, and `qdrant-kafka` currently skips tombstones
  ([qdrant-kafka#8](https://github.com/qdrant/qdrant-kafka/issues/8)), so removing ghost
  vectors on delete is a separate, more advanced beat.
- The auto-generated intermediate topic carries **schemaless JSON**, not Avro: `qdrant-kafka`
  Jackson-serializes the record value and cannot handle Connect Structs, so schemaless JSON
  is its only working input format. The topic is an internal buffer (one producer, one
  consumer) — don't treat it as a public contract. Pipeline-level typing is unaffected:
  the `qdrantSink` node validates id/vector/payload fields at apply time.
- Connector version is pinned to `qdrant-kafka` **1.3.1**: v1.3.2/v1.3.3 ship a packaging
  regression (the shaded gRPC `NameResolverProvider` service file lost its DNS entry) that
  breaks any `host:port` connection with a `unix:///` resolver error.
- First `docker compose up` pulls/builds images; pre-warm once before recording.

## Running against locally built images

Until a TypeStream release ships the `qdrantSink` node and the bundled qdrant-kafka
connector, build the server and kafka-connect images from this repo and point compose
at them:

```bash
# from the repo root (both commands tag the images as :latest, which compose uses by default)
docker build -f docker/Dockerfile.kafka-connect -t ghcr.io/typestreamio/kafka-connect:latest .
./gradlew server:jibDockerBuild

# then, in this directory
docker compose up
```
