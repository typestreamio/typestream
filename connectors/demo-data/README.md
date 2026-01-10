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
│   └── kafka/
│       └── Producer.kt            # Kafka producer wrapper
└── Dockerfile (or Jib)
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
| Medium (1-100 msg/sec) | 1 hour | `wikipedia_changes` |
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
