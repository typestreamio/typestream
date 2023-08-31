# Enriching streams

## Enrich a stream via HTTP api

You can enrich a stream by using the `enrich` data operator with the `http` built-in utility.

Let's use the [country.is](https://country.is) to add country information to our stream of page views.

```bash
cat /dev/kafka/local/topics/page_views | enrich { view -> http "https://api/country.is/#{$view.ip_address}"
```
