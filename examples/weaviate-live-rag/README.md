# Live RAG: Postgres → TypeStream → Weaviate

A self-contained demo of **continuously-fresh RAG**. A support chatbot answers from a
Weaviate index of help-center docs. The source of truth is Postgres — and the moment a
row changes, TypeStream re-embeds it and the bot's answer changes seconds later. No
reindex job, no consumer code, no Kafka to operate.

> Data is fictional ("Northwind"). Nothing here is real.

## What's inside

```
Postgres(help_articles) --Debezium CDC--> Kafka --> TypeStream pipeline
   (kafkaSource unwrapCdc -> embeddingGenerator -> kafkaSink: help_article_embeddings)
   --> Weaviate sink connector --> Weaviate(HelpArticle, vectorizer:none)
                                          ^ nearVector
   chatbot (Node+Express) ----------------/   embed question -> top-3 -> grounded answer
```

The chatbot queries Weaviate directly; TypeStream's only job is keeping Weaviate fresh.

## Run it

```bash
cp .env.example .env          # add your OPENAI_API_KEY
docker compose up             # wait for the `bootstrap` container to print "Demo is live."
```

Then open **http://localhost:8000**.

The `bootstrap` container runs once: it registers the CDC connector, creates the Weaviate
collection, applies the TypeStream pipeline, and registers the Weaviate sink connector,
then exits. After that the pipeline is persisted in Kafka and auto-recovers on every restart.

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
shell) is vendor-neutral. Only two files encode "Weaviate":

| File | What changes |
|------|--------------|
| `bootstrap/target.sh` | `create_collection` + `register_sink_connector` for the new sink |
| `chatbot/retriever.js` | how a question becomes documents (`retrieve(q, k) -> [{title, body}]`) |
| `docker-compose.yml` | swap the `weaviate` service block + the chatbot's `WEAVIATE_HOST` |
| `pipeline/*.json` | (only if the sink topic name changes) |

## Notes & limits

- Requires `OPENAI_API_KEY` (server embeds docs; chatbot embeds queries + generates answers).
- Embedding model is pinned to `text-embedding-3-small` on both sides — keep them in sync.
- **Deletes are out of scope** here (insert + update only). Removing ghost vectors on delete
  is a separate, more advanced beat.
- First `docker compose up` pulls/builds images; pre-warm once before recording.
