---
description: Join records from two Kafka topics by key.
---

# Join Two Topics

This guide shows how to join records from two related Kafka topics into a single enriched stream.

## Prerequisites

- TypeStream [installed](../installation.mdx) and running
- Two topics with related data (the demo CDC topics `dbserver.public.orders` and `dbserver.public.users` work well)

## Key-based join

TypeStream joins two streams by matching record keys. Records with the same key from both topics are merged into a single output record containing all fields from both sides.

import Tabs from "@theme/Tabs";
import TabItem from "@theme/TabItem";

<Tabs>
  <TabItem value="cli" label="CLI DSL" default>

```sh
cat /dev/kafka/local/topics/dbserver.public.orders | join /dev/kafka/local/topics/dbserver.public.users > /dev/kafka/local/topics/orders_enriched
```

  </TabItem>
  <TabItem value="config" label="Config-as-Code">

:::note
Joins are currently available in the CLI DSL only. Config-as-code support for joins is planned.
:::

  </TabItem>
  <TabItem value="gui" label="GUI">

1. Drag a **Postgres Source** (or Kafka Source) for the orders topic
2. Add a **Join** node and connect the source to it
3. Configure the join's `with` field to point to the users topic
4. Add a **Kafka Sink** and connect it to the join output
5. Click **Create Job**

  </TabItem>
</Tabs>

## Output schema

The join merges both schemas into a combined struct. If orders has `(id, user_id, amount)` and users has `(id, name, email)`, the output contains all six fields.

Since the output type differs from either input, the encoding defaults to JSON (see [schema propagation](../concepts/schema-propagation.md#encoding-rules)).

## See also

- [Node Reference: Join](../reference/node-reference.md#join) -- full configuration details
- [Set Up Postgres CDC](setup-postgres-cdc.md) -- how to get CDC topics from PostgreSQL
