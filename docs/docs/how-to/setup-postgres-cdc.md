---
description: Set up Change Data Capture from PostgreSQL using Debezium.
---

# Set Up Postgres CDC

This guide shows how to capture changes from a PostgreSQL database and process them as a streaming pipeline in TypeStream.

## Prerequisites

- TypeStream [installed](../installation.mdx) and running with `typestream local dev`
- The local stack includes a pre-configured PostgreSQL instance with logical replication enabled

## How it works

TypeStream uses [Debezium](https://debezium.io/) via Kafka Connect to capture row-level changes from PostgreSQL:

1. PostgreSQL is configured with `wal_level = logical` for change data capture
2. On startup, TypeStream auto-registers a Debezium connector for the local PostgreSQL instance
3. Debezium streams INSERT/UPDATE/DELETE events into Kafka topics
4. Topics appear in TypeStream's virtual filesystem as `/dev/kafka/local/topics/dbserver.public.<table>`

## Verify CDC topics

After starting the local environment, seed the database:

```bash
typestream local seed
```

Then check for CDC topics:

```bash
echo 'ls /dev/kafka/local/topics' | typestream
```

You should see topics like:

```
dbserver.public.orders
dbserver.public.users
dbserver.public.file_uploads
```

## Read CDC records

CDC records arrive in Debezium's envelope format, which wraps each change in metadata (before/after values, operation type, source info). Use `unwrapCdc` to extract just the row data.

import Tabs from "@theme/Tabs";
import TabItem from "@theme/TabItem";

<Tabs>
  <TabItem value="cli" label="CLI DSL" default>

```sh
cat /dev/kafka/local/topics/dbserver.public.orders
```

  </TabItem>
  <TabItem value="config" label="Config-as-Code">

```json
{
  "name": "orders-stream",
  "version": "1",
  "description": "Stream orders from Postgres CDC",
  "graph": {
    "nodes": [
      {
        "id": "source-1",
        "postgresSource": {
          "topicPath": "/local/topics/dbserver.public.orders"
        }
      },
      {
        "id": "sink-1",
        "kafkaSink": {
          "topicName": "orders_clean"
        }
      }
    ],
    "edges": [
      { "fromId": "source-1", "toId": "sink-1" }
    ]
  }
}
```

  </TabItem>
  <TabItem value="gui" label="GUI">

1. Drag a **Postgres Source** node onto the canvas
2. Select the `dbserver.public.orders` topic -- CDC unwrapping is enabled automatically for Postgres sources
3. Add downstream nodes as needed

  </TabItem>
</Tabs>

## Join CDC streams

A common pattern is joining CDC data from related tables. For example, enriching orders with user information:

```sh
cat /dev/kafka/local/topics/dbserver.public.orders | join /dev/kafka/local/topics/dbserver.public.users > /dev/kafka/local/topics/orders_enriched
```

:::note
Joins are currently available in the CLI DSL only. Config-as-code support for joins is planned.
:::

## Topic naming convention

Debezium uses the format `<server>.<schema>.<table>` for topic names:

| PostgreSQL | Kafka Topic |
|-----------|-------------|
| `public.orders` | `dbserver.public.orders` |
| `public.users` | `dbserver.public.users` |
| `public.file_uploads` | `dbserver.public.file_uploads` |

The `dbserver` prefix comes from the Debezium connector's `topic.prefix` configuration.

## See also

- [CDC and Debezium](../concepts/cdc-and-debezium.md) -- how CDC works under the hood
- [Node Reference: StreamSource](../reference/node-reference.md#streamsource) -- `unwrapCdc` and encoding options
