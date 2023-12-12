---
description: Learn how to filter streams using the enrich operator.
---

# Enriching streams

The purpose of this document is to explain how to use the `enrich` operator via
examples. If you're looking for a formal specification of its usage, refer to
the [specification](reference/language/operators.md#enrich) document.

## Enrich a stream via HTTP requests

You can enrich a stream by using the `enrich` data operator with the `http`
built-in utility.

Let's use the [country.is](https://country.is) service to add country
information to our stream of page views.

```bash
cat /dev/kafka/local/topics/page_views | enrich { view -> http "https://api.country.is/#{$view.ip_address}" }
```

Here's this example in action:

![enrich](../../../assets/vhs/enrich.gif)
