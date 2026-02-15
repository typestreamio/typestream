---
description: Add geographic information to events using IP address lookup.
---

# Geo-Enrich Events

This guide shows how to use the GeoIP node to add geographic information (country, city) to events that contain an IP address field.

TypeStream bundles a MaxMind GeoLite2 database -- no external API keys or services needed.

## Prerequisites

- TypeStream [installed](../installation.mdx) and running
- A topic with records containing an IP address field (the demo `web_visits` topic works)

## Enrich with GeoIP

The GeoIP node takes two parameters:
- `ipField` -- the name of the field containing the IP address
- `outputField` -- the name of the new field to add with the geographic result

import Tabs from "@theme/Tabs";
import TabItem from "@theme/TabItem";

<Tabs>
  <TabItem value="cli" label="CLI DSL" default>

```sh
cat /dev/kafka/local/topics/web_visits | enrich { visit -> http "https://api.country.is/#{$visit.ip_address}" }
```

:::note
The CLI DSL uses the `enrich` operator with a block expression for HTTP-based enrichment. The built-in GeoIP node is available in config-as-code and the GUI.
:::

  </TabItem>
  <TabItem value="config" label="Config-as-Code">

```json
{
  "name": "webvisits-enriched",
  "version": "1",
  "description": "Enrich web visits with geolocation from IP address",
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
        "id": "geoip-1",
        "geoIp": {
          "ipField": "ip_address",
          "outputField": "country_code"
        }
      },
      {
        "id": "sink-1",
        "sink": {
          "output": { "path": "/dev/kafka/local/topics/web_visits_enriched" },
          "encoding": "AVRO"
        }
      }
    ],
    "edges": [
      { "fromId": "source-1", "toId": "geoip-1" },
      { "fromId": "geoip-1", "toId": "sink-1" }
    ]
  }
}
```

Apply it:

```bash
typestream apply webvisits-enriched.typestream.json
```

  </TabItem>
  <TabItem value="gui" label="GUI">

1. Drag a **Kafka Source** and select the `web_visits` topic
2. Drag a **GeoIP** node and connect it to the source
3. Set `ipField` to `ip_address` and `outputField` to `country_code`
4. Drag a **Kafka Sink** and set the output topic
5. Click **Create Job**

The GeoIP node will show the added `country_code` field in the schema preview.

  </TabItem>
</Tabs>

## Schema behavior

The GeoIP node validates at compile time that the `ipField` exists in the input schema. It adds the `outputField` (type: string) to the output schema, so downstream nodes can reference it.

For example, you can chain a filter after enrichment:

```json
{
  "id": "filter-1",
  "filter": {
    "predicate": { "expr": ".country_code == \"US\"" }
  }
}
```

## See also

- [Node Reference: GeoIp](../reference/node-reference.md#geoip) -- full configuration details
- [Schema Propagation](../concepts/schema-propagation.md) -- how TypeStream tracks schema changes through the pipeline
