# Live RAG: Postgres → TypeStream → Qdrant

A self-contained demo of **continuously-fresh RAG**. A support chatbot answers from a
Qdrant collection of help-center docs. The source of truth is Postgres — and the moment a
row changes, TypeStream re-embeds it and the bot's answer changes seconds later. No
reindex job, no consumer code, no Kafka to operate.

> Data is fictional ("Northwind"). Nothing here is real.

## What's inside

```
Postgres(help_articles) --Debezium CDC--> Kafka --> TypeStream pipeline
   (kafkaSource unwrapCdc -> embeddingGenerator -> qdrantSink)
                                   |
        server reshapes -> {id, vector, payload} JSON + provisions the connector
                                   |
   --> Qdrant Kafka connector --> Qdrant(help_articles, size 1536, Cosine)
                                          ^ vector search
   chatbot (Node+Express) ----------------/   embed question -> top-3 -> grounded answer
```

The chatbot queries Qdrant directly; TypeStream's only job is keeping Qdrant fresh.

Unlike the [Weaviate sibling](../weaviate-live-rag) (where the connector is registered by
hand), **Qdrant is a first-class TypeStream sink**: the pipeline declares a `qdrantSink`
node, and the server reshapes each record into Qdrant's `{id, vector, payload}` envelope
and provisions the out-of-the-box `io.qdrant.kafka.QdrantSinkConnector` automatically when
the pipeline is applied.

## Run it

```bash
cp .env.example .env          # add your OPENAI_API_KEY
docker compose up             # wait for the `bootstrap` container to print "Demo is live."
```

Then open **http://localhost:8000**.

> Requires server + kafka-connect images that include Qdrant support (TypeStream ≥ the
> release that adds the `qdrantSink` node). When building from source, publish/pull those
> images or point `TYPESTREAM_VERSION` at the matching tag.

The `bootstrap` container runs once: it registers the CDC connector, creates the Qdrant
collection, and applies the TypeStream pipeline (which makes the server provision the Qdrant
sink connector), then exits. After that the pipeline is persisted in Kafka and auto-recovers
on every restart.

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
shell) is vendor-neutral. See [`../README.md`](../README.md) for the multi-target template.
For Qdrant, only these encode the target:

| File | What changes |
|------|--------------|
| `pipeline/*.json` | uses a `qdrantSink` node (server provisions the connector) |
| `bootstrap/target.sh` | `create_collection` (Qdrant has no auto-schema); `register_sink_connector` is a no-op |
| `chatbot/retriever.js` | how a question becomes documents (`retrieve(q, k) -> [{title, body}]`) |
| `docker-compose.yml` | the `qdrant` service block + the chatbot's `QDRANT_URL` |

## Notes & limits

- Requires `OPENAI_API_KEY` (server embeds docs; chatbot embeds queries + generates answers).
- Embedding model is pinned to `text-embedding-3-small` (1536 dims) on both sides — keep them
  in sync, and keep the collection's `size` matching the model.
- Point ids come from the Postgres `id` (an integer); Qdrant requires integer or UUID ids.
- **Deletes are out of scope** here (insert + update only). Removing ghost vectors on delete
  is a separate, more advanced beat.
- First `docker compose up` pulls/builds images; pre-warm once before recording.
