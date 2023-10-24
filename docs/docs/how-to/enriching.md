---
description: Learn how to filter streams using the enrich operator.
---

# Enriching streams

## Enrich a stream via HTTP requests

You can enrich a stream by using the `enrich` data operator with the `http` built-in utility.

:::note
The `http` utility is WIP and only supports get requests at the moment. We're working on adding more features to it. If you'd like to contribute, open an issue and we'll get you started!
:::

Let's use the [country.is](https://country.is) to add country information to our stream of page views.

```bash
cat /dev/kafka/local/topics/page_views | enrich { view -> http "https://api/country.is/#{$view.ip_address}" }
```

Here's this example in action:

![enrich](../../../assets/vhs/enrich.gif)
