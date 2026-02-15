---
description: Count, group, and aggregate streaming data in real time.
---

# Real-Time Aggregations

This guide shows how to build aggregation pipelines that count, group, and reduce streaming data into queryable materialized views.

## Prerequisites

- TypeStream [installed](../installation.mdx) and running
- Sample data seeded (`typestream local seed`)

## Count records by field

Group records by a field and count occurrences. The result is a KTable that updates in real time as new records arrive.

import Tabs from "@theme/Tabs";
import TabItem from "@theme/TabItem";

<Tabs>
  <TabItem value="cli" label="CLI DSL" default>

```sh
cat /dev/kafka/local/topics/web_visits | wc
```

  </TabItem>
  <TabItem value="config" label="Config-as-Code">

```json
{
  "name": "visits-by-country",
  "version": "1",
  "description": "Count web visits grouped by country",
  "graph": {
    "nodes": [
      {
        "id": "source-1",
        "streamSource": {
          "dataStream": { "path": "/dev/kafka/local/topics/web_visits" },
          "encoding": "AVRO"
        }
      },
      {
        "id": "group-1",
        "group": { "keyMapperExpr": ".country" }
      },
      {
        "id": "count-1",
        "count": {}
      }
    ],
    "edges": [
      { "fromId": "source-1", "toId": "group-1" },
      { "fromId": "group-1", "toId": "count-1" }
    ]
  }
}
```

  </TabItem>
  <TabItem value="gui" label="GUI">

1. Drag a **Kafka Source** and select the `web_visits` topic
2. Drag a **Materialized View** node and connect it
3. Set the `groupByField` to `country` and the aggregation type to `count`
4. Click **Create Job**

  </TabItem>
</Tabs>

## Windowed count

Count records within a tumbling time window (e.g., visits per country per minute):

```json
{
  "id": "windowed-count-1",
  "windowedCount": { "windowSizeSeconds": 60 }
}
```

Replace the `count` node with `windowedCount` in the pipeline above. Each window produces a separate count that closes after the specified duration.

## Keep latest value per key

Use `reduceLatest` to build a lookup table that always holds the most recent value for each key:

```json
{
  "id": "reduce-1",
  "reduceLatest": {}
}
```

This is useful for maintaining a current-state view from a changelog stream (e.g., the latest order status per order ID).

## Query materialized views

Once an aggregation pipeline is running, you can query its state store via the `StateQueryService`:

- **List stores**: See all queryable state stores from running jobs
- **Get all values**: Stream all key-value pairs from a store
- **Get value**: Look up a single value by key

The GUI's job detail page shows materialized view data automatically.

## See also

- [Node Reference: Count](../reference/node-reference.md#count) -- count node details
- [Node Reference: WindowedCount](../reference/node-reference.md#windowedcount) -- windowed count details
- [Node Reference: ReduceLatest](../reference/node-reference.md#reducelatest) -- reduce latest details
- [Node Reference: Group](../reference/node-reference.md#group) -- group node details
