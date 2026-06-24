// Vendor-neutral RAG chatbot. Retrieval is delegated to ./retriever.js so this
// file never changes when the demo is retargeted to another datastore.
import express from "express";
import OpenAI from "openai";
import { retrieve } from "./retriever.js";

const CHAT_MODEL = process.env.CHAT_MODEL || "gpt-4o-mini";
const openai = new OpenAI({ apiKey: process.env.OPENAI_API_KEY });

// Grounding: makes the pre-INSERT "I don't have that information" honest rather
// than a hallucination -- and that honest "I can't answer yet" is itself a demo beat.
const SYSTEM_PROMPT =
  "You are Northwind's support assistant. Answer only from the provided context. " +
  "If the answer isn't in the context, say you don't have that information. " +
  "Be concise.";

const app = express();
app.use(express.json());
app.use(express.static("public"));

app.post("/chat", async (req, res) => {
  const question = (req.body?.message || "").trim();
  if (!question) return res.status(400).json({ error: "message is required" });

  try {
    const docs = await retrieve(question, 3);
    const context = docs.map((d) => `Title: ${d.title}\n${d.body}`).join("\n\n");

    const completion = await openai.chat.completions.create({
      model: CHAT_MODEL,
      temperature: 0, // deterministic: same question -> same words, so only freshness changes on camera
      messages: [
        { role: "system", content: SYSTEM_PROMPT },
        { role: "user", content: `Context:\n${context || "(no matching documents)"}\n\nQuestion: ${question}` },
      ],
    });

    res.json({
      answer: completion.choices[0].message.content,
      sources: docs.map((d) => d.title),
    });
  } catch (err) {
    console.error(err);
    res.status(500).json({ error: "retrieval or completion failed; is the demo bootstrapped yet?" });
  }
});

const PORT = process.env.PORT || 8000;
app.listen(PORT, () => console.log(`chatbot listening on http://localhost:${PORT}`));
