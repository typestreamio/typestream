// ---------------------------------------------------------------------------
// TARGET-SPECIFIC SEAM (Qdrant).
// Owns the entire "question -> documents" step, embedding included, so server.js
// stays vendor-neutral. To retarget the demo, this is one of only two files you
// rewrite (the other is bootstrap/target.sh). Keep the export shape identical:
//
//   retrieve(question: string, k: number) -> Promise<Array<{title, body}>>
//
// (A graph/SQL backend could ignore embeddings entirely and still satisfy this.)
// ---------------------------------------------------------------------------
import { QdrantClient } from "@qdrant/js-client-rest";
import OpenAI from "openai";

const COLLECTION = process.env.COLLECTION || "help_articles";
const EMBEDDING_MODEL = process.env.EMBEDDING_MODEL || "text-embedding-3-small";

const openai = new OpenAI({ apiKey: process.env.OPENAI_API_KEY });
const client = new QdrantClient({
  url: process.env.QDRANT_URL || "http://qdrant:6333",
});

export async function retrieve(question, k = 3) {
  // Query vector MUST use the same model the pipeline embedded docs with,
  // or vector search is meaningless.
  const embedding = await openai.embeddings.create({
    model: EMBEDDING_MODEL,
    input: question,
  });
  const vector = embedding.data[0].embedding;

  const hits = await client.search(COLLECTION, {
    vector,
    limit: k,
    with_payload: true,
  });

  const docs = hits.map((h) => ({
    title: h.payload?.title,
    body: h.payload?.body,
    score: h.score,
  }));

  // Dump the Qdrant query result to the console (visible via `docker compose logs -f chatbot`).
  console.log(`[retrieve] q=${JSON.stringify(question)} ->`);
  docs.forEach((d, i) =>
    console.log(`  ${i + 1}. score=${d.score?.toFixed(4)}  ${d.title}`)
  );

  return docs;
}
