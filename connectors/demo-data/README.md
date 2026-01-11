# Demo Data Generator

Standalone Kotlin app that produces real-time data streams to Kafka topics for demos and testing.

## Architecture

```
connectors/demo-data/
├── build.gradle.kts
├── src/main/kotlin/io/typestream/connectors/
│   ├── Main.kt                    # Entry point, CLI routing
│   ├── Config.kt                  # Config parsing
│   ├── coinbase/
│   │   └── CoinbaseConnector.kt   # WebSocket client
│   ├── wikipedia/
│   │   └── WikipediaConnector.kt  # SSE client
│   ├── webvisits/
│   │   ├── WebVisitsConnector.kt  # Fake web traffic generator
│   │   ├── WebVisitGenerator.kt   # Visit data generation
│   │   ├── TrafficSimulator.kt    # Realistic timing patterns
│   │   └── geo/
│   │       ├── CidrRegistry.kt    # CIDR block loading
│   │       └── CountryIpGenerator.kt  # Country-specific IPs
│   └── kafka/
│       └── Producer.kt            # Kafka producer wrapper
├── src/main/resources/cidr/       # Country IP ranges (CIDR format)
└── Dockerfile
```

## How It Works

Each connector:
1. Connects to an external data source (WebSocket, SSE, or polling)
2. Receives real-time events
3. Transforms to Avro/Proto records
4. Produces to a dedicated Kafka topic

## Running

Via Docker Compose (planned):
```yaml
demo-data:
  image: typestream/demo-data
  depends_on:
    redpanda:
      condition: service_healthy
  environment:
    - TYPESTREAM_CONFIG
  command: ["coinbase", "--products", "ETH-USD,BTC-USD"]
```

Via Gradle (local dev):
```bash
./gradlew connectors:demo-data:run --args="coinbase --products ETH-USD,BTC-USD"
```

## Web Visits Connector

The `webvisits` connector generates synthetic web traffic with **geocodable IP addresses**. This is useful for demonstrating geocoding/enrichment pipelines.

### Usage

```bash
# Default: ~10 events/sec, weighted country distribution (US ~35%)
./gradlew connectors:demo-data:run --args="webvisits"

# Custom rate
./gradlew connectors:demo-data:run --args="webvisits --rate 50"

# Specific countries only
./gradlew connectors:demo-data:run --args="webvisits --countries US,GB,DE"

# Docker
docker run typestream/demo-data webvisits --rate 20
```

### Schema Fields

| Field | Type | Description |
|-------|------|-------------|
| `ip_address` | string | Geocodable IP from country CIDR blocks |
| `timestamp` | timestamp | Event time |
| `url_path` | string | e.g., `/products/123`, `/blog/post-42` |
| `http_method` | string | GET (90%), POST (7%), PUT/DELETE (3%) |
| `status_code` | int | 200 (95%), 301/404/500/503 (5%) |
| `response_bytes` | long | 500 - 500,000 |
| `user_agent` | string | Realistic browser/bot user agents |
| `referrer` | string? | Google, Facebook, direct, or internal |
| `session_id` | string | UUID, 30% returning visitors |
| `page_load_time_ms` | int | 50 - 3000 ms |
| `is_bot` | boolean | 5% bot traffic |
| `device_type` | string | desktop, mobile, tablet |
| `browser` | string | Chrome, Firefox, Safari, Edge, etc. |
| `os` | string | Windows, macOS, Linux, Android, iOS |

### How IP Generation Works

IPs are generated from real country-specific CIDR blocks, so they geocode correctly:

1. **CIDR files** in `src/main/resources/cidr/{country}.cidr` contain IP ranges like `3.0.0.0/8`
2. **CidrRegistry** loads and parses these using Apache Commons Net `SubnetUtils`
3. **CountryIpGenerator** selects a country (weighted distribution), picks a random CIDR block, generates a random IP within that range

**Included countries** (15): US, GB, DE, FR, CA, AU, BR, IN, JP, CN, ES, IT, NL, SE, SG

### Downloading More IP Data

The CIDR files come from [IPdeny](https://www.ipdeny.com/ipblocks/). To add more countries or update existing data:

```bash
# Download aggregated zone file for a country (2-letter ISO code)
curl -o src/main/resources/cidr/mx.cidr \
  "https://www.ipdeny.com/ipblocks/data/aggregated/mx-aggregated.zone"

# Download multiple countries
for cc in mx ar co; do
  curl -o src/main/resources/cidr/${cc}.cidr \
    "https://www.ipdeny.com/ipblocks/data/aggregated/${cc}-aggregated.zone"
done

# Update all existing countries
for f in src/main/resources/cidr/*.cidr; do
  cc=$(basename "$f" .cidr)
  curl -o "$f" "https://www.ipdeny.com/ipblocks/data/aggregated/${cc}-aggregated.zone"
done
```

After adding new countries, update `CidrRegistry.COUNTRIES` list in `CidrRegistry.kt`.

**Alternative sources:**
- [herrbischoff/country-ip-blocks](https://github.com/herrbischoff/country-ip-blocks) - Updated hourly from RIRs
- [IP2Location](https://lite.ip2location.com/ip-address-ranges-by-country) - Free with registration

### Verifying Generated IPs

Test that generated IPs geocode correctly:
- https://www.maxmind.com/en/geoip-demo
- https://www.iplocation.net/

## Schema Registration

Schemas are registered automatically with the Confluent Schema Registry on first produce.

**How it works:**
1. Define Avro schema in `src/main/avro/<type>.avsc`
2. Avro Gradle plugin generates Kotlin classes at build time
3. `KafkaAvroSerializer` registers schema under `<topic>-value` subject on first message

**Example schema** (`src/main/avro/crypto-ticker.avsc`):
```json
{
  "namespace": "io.typestream.connectors.avro",
  "type": "record",
  "name": "CryptoTicker",
  "fields": [
    {"name": "product_id", "type": "string"},
    {"name": "price", "type": "string"},
    {"name": "volume_24h", "type": "string"},
    {"name": "timestamp", "type": {"type": "long", "logicalType": "timestamp-millis"}}
  ]
}
```

This generates `io.typestream.connectors.avro.CryptoTicker` class, and when the connector produces to `crypto_tickers` topic, the schema is auto-registered as `crypto_tickers-value`.

## Topic Retention Strategy

Topics are created with retention configs to avoid filling disk on high-volume streams:

| Volume | Retention | Example Topics |
|--------|-----------|----------------|
| High (100+ msg/sec) | 15 minutes | `crypto_tickers` |
| Medium (1-100 msg/sec) | 1 hour | `wikipedia_changes`, `web_visits` |
| Low (<1 msg/sec) | 24 hours | `earthquakes`, `hackernews_stories` |

**Implementation:**
```kotlin
fun createTopic(name: String, retentionMs: Long) {
    val topic = NewTopic(name, 3, 1.toShort())
        .configs(mapOf(
            "retention.ms" to retentionMs.toString(),
            "cleanup.policy" to "delete"
        ))
    adminClient.createTopics(listOf(topic)).all().get(30, TimeUnit.SECONDS)
}

// Usage
createTopic("crypto_tickers", 15 * 60 * 1000)      // 15 min
createTopic("wikipedia_changes", 60 * 60 * 1000)   // 1 hour
createTopic("earthquakes", 24 * 60 * 60 * 1000)    // 24 hours
```

Each connector is responsible for creating its topic with appropriate retention on startup.

## Design Principles

- **One app, many connectors** - Each connector is a subcommand
- **Docker Compose managed** - Start/stop with the rest of the stack
- **Graceful shutdown** - Clean disconnect and flush on SIGTERM
- **Reconnect logic** - Auto-reconnect on connection drops
- **Configurable** - Products, topics, intervals via CLI args or env vars
- **Schema-first** - Avro schemas defined upfront, auto-registered on produce
- **Retention-aware** - High-volume topics expire quickly to save disk
