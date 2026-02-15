---
description: Transform streaming data using OpenAI language models.
---

# Enrich with AI

This guide shows how to use the OpenAI Transformer node to apply LLM prompts to each record in a stream, adding AI-generated fields to your data.

## Prerequisites

- TypeStream [installed](../installation.mdx) and running
- An OpenAI API key set as the `OPENAI_API_KEY` environment variable on the server

## Apply an LLM prompt

The OpenAI Transformer node sends a prompt (which can reference record fields) to an OpenAI model and stores the response in a new output field.

import Tabs from "@theme/Tabs";
import TabItem from "@theme/TabItem";

<Tabs>
  <TabItem value="config" label="Config-as-Code" default>

```json
{
  "name": "wikipedia-with-summary",
  "version": "1",
  "description": "Generate AI summaries for Wikipedia changes",
  "graph": {
    "nodes": [
      {
        "id": "source-1",
        "kafkaSource": {
          "topicPath": "/dev/kafka/local/topics/wikipedia_changes",
          "encoding": "AVRO"
        }
      },
      {
        "id": "ai-1",
        "openAiTransformer": {
          "prompt": "Write a one-sentence summary of this Wikipedia edit to the article: ${title}",
          "outputField": "ai_summary",
          "model": "gpt-4o-mini"
        }
      },
      {
        "id": "sink-1",
        "kafkaSink": {
          "topicName": "wikipedia_with_summaries"
        }
      }
    ],
    "edges": [
      { "fromId": "source-1", "toId": "ai-1" },
      { "fromId": "ai-1", "toId": "sink-1" }
    ]
  }
}
```

  </TabItem>
  <TabItem value="gui" label="GUI">

1. Drag a **Kafka Source** and select a topic
2. Drag an **OpenAI Transformer** node and connect it
3. Set the prompt, output field name, and model
4. Add a **Kafka Sink** for the output
5. Click **Create Job**

The GUI provides a model dropdown populated from the server's available models list.

  </TabItem>
</Tabs>

## Configuration

| Field | Description |
|-------|-------------|
| `prompt` | The prompt template sent to the model. Reference record fields with `${fieldName}`. |
| `outputField` | Name of the new field added to each record with the model's response. |
| `model` | OpenAI model name (e.g. `gpt-4o-mini`, `gpt-4o`). |

## Schema behavior

The OpenAI Transformer adds `outputField` (type: string) to the output schema. Downstream nodes can reference this field for further processing -- for example, generating embeddings from the AI summary.

## See also

- [Node Reference: OpenAiTransformer](../reference/node-reference.md#openaitransformer) -- full node specification
- [Add Semantic Search](add-semantic-search.md) -- chain AI enrichment with vector search
