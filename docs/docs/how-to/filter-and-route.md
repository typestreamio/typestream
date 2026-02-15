---
description: Filter records from a stream and route results to an output topic.
---

# Filter and Route

This guide shows how to filter records from a Kafka topic and route matching results to a new topic.

## Prerequisites

- TypeStream [installed](../installation.mdx) and running
- Demo data generators running (started automatically with `typestream local dev`)

## Filter by content

The simplest filter matches records containing a text string.

import Tabs from "@theme/Tabs";
import TabItem from "@theme/TabItem";

<Tabs>
  <TabItem value="cli" label="CLI DSL" default>

```sh
grep /dev/kafka/local/topics/web_visits "/products"
```

Bare words work too (case-insensitive):

```sh
grep /dev/kafka/local/topics/web_visits products
```

  </TabItem>
  <TabItem value="config" label="Config-as-Code">

```json
{
  "name": "filter-products",
  "version": "1",
  "description": "Filter web visits to product pages",
  "graph": {
    "nodes": [
      {
        "id": "source-1",
        "kafkaSource": {
          "topicPath": "/local/topics/web_visits",
          "encoding": "AVRO"
        }
      },
      {
        "id": "filter-1",
        "filter": {
          "expression": ".url_path ~= \"/products\""
        }
      },
      {
        "id": "sink-1",
        "kafkaSink": {
          "topicName": "product_visits"
        }
      }
    ],
    "edges": [
      { "fromId": "source-1", "toId": "filter-1" },
      { "fromId": "filter-1", "toId": "sink-1" }
    ]
  }
}
```

  </TabItem>
  <TabItem value="gui" label="GUI">

1. Drag a **Kafka Source** and select the `web_visits` topic
2. Drag a **Filter** node, connect it, and set the expression to `.url_path ~= "/products"`
3. Drag a **Kafka Sink** and set the output topic
4. Click **Create Job**

  </TabItem>
</Tabs>

## Filter by field

Use predicate expressions for field-based filtering:

```sh
grep /dev/kafka/local/topics/web_visits [.status_code > 399]
```

Combine conditions with `&&` and `||`:

```sh
grep /dev/kafka/local/topics/web_visits [ .status_code == 200 || .url_path ~= '/products' ]
```

### Predicate operators

| Operator | Description |
|----------|-------------|
| `==` | Strict equality (same type required) |
| `!=` | Strict inequality |
| `>`, `>=`, `<`, `<=` | Numeric comparison |
| `~=` | Contains (case-insensitive) |

### Invert matching

Use `-v` to select records that do **not** match:

```sh
grep -v /dev/kafka/local/topics/web_visits "/health"
```

### Filter by key

Use `-k` to match against the record key instead of the value:

```sh
grep -k /dev/kafka/local/topics/web_visits "some-key"
```

## Route to an output topic

Use `>` to write filtered results to a new Kafka topic:

```sh
grep /dev/kafka/local/topics/web_visits [.status_code > 399] > /dev/kafka/local/topics/error_visits
```

The output topic is created automatically. Encoding follows the input: if the source is Avro, the output will also be Avro (since the schema is unchanged by filtering).

## See also

- [Node Reference: Filter](../reference/node-reference.md#filter) -- full config fields and schema behavior
- [Data Operators: grep](../reference/language/operators.md#grep) -- DSL syntax reference
