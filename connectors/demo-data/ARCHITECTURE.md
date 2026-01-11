# Demo Data Connector Architecture

## Overview

The demo-data directory is a standalone Kotlin application that generates real-time synthetic and live data streams, publishing them to Kafka topics. It provides realistic data for development, testing, and demonstrations of TypeStream capabilities.

## Tech Stack

| Component | Technology |
|-----------|------------|
| Language | Kotlin |
| Build | Gradle (with Avro plugin) |
| CLI | Clikt |
| Kafka | Apache Kafka clients |
| Schemas | Avro (auto-compiled) |
| HTTP | OkHttp (WebSocket, SSE) |
| Data Generation | DataFaker |

## Directory Structure

```
connectors/demo-data/
├── src/main/kotlin/io/typestream/connectors/
│   ├── Main.kt                      # CLI entry point
│   ├── Config.kt                    # Environment config
│   ├── kafka/
│   │   └── Producer.kt              # Kafka producer wrapper
│   ├── coinbase/
│   │   └── CoinbaseConnector.kt     # Crypto price tickers
│   ├── wikipedia/
│   │   └── WikipediaConnector.kt    # Wiki edit events
│   └── webvisits/
│       ├── WebVisitsConnector.kt    # Synthetic web traffic
│       ├── WebVisitGenerator.kt     # Data generation logic
│       ├── TrafficSimulator.kt      # Realistic timing patterns
│       └── geo/                     # IP geolocation
├── src/main/resources/cidr/         # Country IP ranges (15 countries)
├── src/main/avro/                   # Avro schemas
└── Dockerfile                       # Multi-stage build
```

## Available Connectors

### Coinbase Crypto Tickers
- **Source**: WebSocket to `wss://ws-feed.exchange.coinbase.com`
- **Data**: Real-time cryptocurrency prices (BTC-USD, ETH-USD, etc.)
- **Volume**: 100-1000+ messages/sec
- **Topic**: `crypto_tickers`
- **CLI**: `coinbase --products BTC-USD,ETH-USD`

### Wikipedia Recent Changes
- **Source**: Server-Sent Events from Wikimedia
- **Data**: Real-time wiki edits across all Wikipedias
- **Volume**: 100-500 messages/sec
- **Topic**: `wikipedia_changes`
- **CLI**: `wikipedia --wikis enwiki,dewiki`

### Web Visits (Synthetic)
- **Source**: Generated synthetic traffic
- **Data**: Web visit events with geocodable IPs
- **Volume**: Configurable (~10/sec default)
- **Topic**: `web_visits`
- **CLI**: `webvisits --rate 10 --countries US,GB,DE`

Key features:
- Geocodable IPs from real country CIDR blocks (15 countries)
- Session simulation (30% returning visitors)
- Realistic browser/device user agents
- Time-of-day traffic patterns

## Data Flow

```
External Source (WebSocket/SSE) or Synthetic Generator
    ↓
Connector Class (parses/generates events)
    ↓
Transform to Avro object
    ↓
Producer (with schema registry)
    ↓
Kafka Topic
```

## Avro Schemas

Located in `src/main/avro/`:

| Schema | Key Fields |
|--------|------------|
| `WebVisit.avsc` | ip_address, url_path, session_id, device_type, browser |
| `CryptoTicker.avsc` | product_id, price, volume_24h, bid, ask |
| `WikipediaChange.avsc` | wiki, title, user, edit_type, bot_flag |

Schemas are auto-compiled to Kotlin classes at build time.

## Docker Compose Integration

Three services run in the dev stack (see `cli/pkg/compose/compose.yml`):

```yaml
demo-data-coinbase:   # Crypto prices
demo-data-wikipedia:  # Wiki edits
demo-data-webvisits:  # Web traffic
```

All depend on `redpanda` (Kafka) being healthy.

## Configuration

Environment variables:
- `KAFKA_BOOTSTRAP_SERVERS` (default: `localhost:19092`)
- `SCHEMA_REGISTRY_URL` (default: `http://localhost:18081`)

## Usage

**Local development:**
```bash
./gradlew connectors:demo-data:run --args="webvisits --rate 20"
```

**Full stack:**
```bash
cd cli && ./typestream local dev start
```

## Key Files

| File | Purpose |
|------|---------|
| `Main.kt` | CLI subcommand routing |
| `Producer.kt` | Kafka producer with schema registry |
| `WebVisitGenerator.kt` | Synthetic data generation |
| `CidrRegistry.kt` | Country IP range loading |
| `TrafficSimulator.kt` | Realistic timing patterns |
