// ---------------------------------------------------------------------------
// TARGET-SPECIFIC SEAM (Weaviate).
// Owns the entire "question -> documents" step, embedding included, so server.js
// stays vendor-neutral. To retarget the demo, this is one of only two files you
// rewrite (the other is bootstrap/target.sh). Keep the export shape identical:
//
//   retrieve(question: string, k: number) -> Promise<Array<{title, body}>>
//
// (A graph/SQL backend could ignore embeddings entirely and still satisfy this.)
// ---------------------------------------------------------------------------
import weaviate from "weaviate-ts-client";
import OpenAI from "openai";

const COLLECTION = process.env.COLLECTION || "HelpArticle";
const EMBEDDING_MODEL = process.env.EMBEDDING_MODEL || "text-embedding-3-small";

const openai = new OpenAI({ apiKey: process.env.OPENAI_API_KEY });
const client = weaviate.client({
  scheme: process.env.WEAVIATE_SCHEME || "http",
  host: process.env.WEAVIATE_HOST || "weaviate:8080",
});

export async function retrieve(question, k = 3) {
  // Query vector MUST use the same model the pipeline embedded docs with,
  // or nearVector search is meaningless.
  const embedding = await openai.embeddings.create({
    model: EMBEDDING_MODEL,
    input: question,
  });
  const vector = embedding.data[0].embedding;

  const result = await client.graphql
    .get()
    .withClassName(COLLECTION)
    .withFields("title body _additional { distance }")
    .withNearVector({ vector })
    .withLimit(k)
    .do();

  const docs = result?.data?.Get?.[COLLECTION] ?? [];

  // Dump the Weaviate query result to the console (visible via `docker compose logs -f chatbot`).
  console.log(`[retrieve] q=${JSON.stringify(question)} ->`);
  docs.forEach((d, i) =>
    console.log(`  ${i + 1}. dist=${d._additional?.distance?.toFixed(4)}  ${d.title}`)
  );

  return docs;
}
