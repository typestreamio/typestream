# Demo Data Connector Architecture

> Part of [TypeStream Architecture](../../ARCHITECTURE.md)

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
│   │   ├── Producer.kt              # Kafka producer wrapper
│   │   └── MessageSender.kt         # Interface for testability
│   ├── coinbase/
│   │   └── CoinbaseConnector.kt     # Crypto price tickers
│   ├── wikipedia/
│   │   └── WikipediaConnector.kt    # Wiki edit events
│   ├── webvisits/
│   │   ├── WebVisitsConnector.kt    # Synthetic web traffic
│   │   ├── WebVisitGenerator.kt     # Data generation logic
│   │   ├── TrafficSimulator.kt      # Realistic timing patterns
│   │   └── geo/                     # IP geolocation
│   └── fileuploads/
│       └── FileUploadsConnector.kt  # PostgreSQL file upload records (CDC path)
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

### File Uploads (PostgreSQL CDC)
- **Source**: Inserts records into PostgreSQL (captured by Debezium CDC)
- **Data**: Synthetic file upload records with actual sample files on disk
- **Volume**: Configurable (~1/sec default, max 50 records)
- **Topic**: Debezium CDC topic (via PostgreSQL -> Debezium -> Kafka)
- **CLI**: `fileuploads --rate 1 --max 50 --output-dir /tmp/typestream-files`

Key features:
- Creates actual sample files on disk (text, PDF, HTML, CSV)
- Inserts records into PostgreSQL `file_uploads` table
- Debezium captures CDC events and publishes to Kafka
- Designed for demonstrating the full CDC pipeline

## Data Flow

### Kafka-Direct Connectors (Coinbase, Wikipedia, Web Visits)
```
External Source (WebSocket/SSE) or Synthetic Generator
    ↓
Connector Class (parses/generates events)
    ↓
Transform to Avro object
    ↓
Producer (with Schema Registry)
    ↓
Kafka Topic
```

### CDC Connector (File Uploads)
```
FileUploadsConnector generates records
    ↓
Inserts into PostgreSQL table
    ↓
Debezium captures change events
    ↓
Publishes to Kafka CDC topic
```

## Avro Schemas

Located in `src/main/avro/`:

| Schema | Key Fields |
|--------|------------|
| `WebVisit.avsc` | ip_address, url_path, session_id, device_type, browser |
| `CryptoTicker.avsc` | product_id, price, volume_24h, bid, ask |
| `WikipediaChange.avsc` | wiki, title, user, type (edit_type), bot |
| `FileUpload.avsc` | id, file_path, file_name, content_type, uploaded_by |

Schemas are auto-compiled to Kotlin classes at build time via the Avro Gradle plugin.

## Docker Compose Integration

Services run in the dev stack (see `cli/pkg/compose/compose.yml`):

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

File Uploads additional config:
- `POSTGRES_JDBC_URL` (default: `jdbc:postgresql://localhost:5432/demo`)
- `POSTGRES_USER` (default: `typestream`)
- `POSTGRES_PASSWORD` (default: `typestream`)

## Usage

**Local development:**
```bash
./gradlew connectors:demo-data:run --args="webvisits --rate 20"
```

**Full stack:**
```bash
cd cli && ./typestream local dev start
```

## Integration Points

### Kafka (Redpanda)
- `Producer.kt` wraps `KafkaProducer` with Avro serialization
- `MessageSender` interface allows test doubles
- Schema Registry integration for Avro schema registration

### Schema Registry
- Producer auto-registers Avro schemas on first produce
- Schemas use namespace `io.typestream.connectors.avro`

### External APIs
- **Coinbase**: WebSocket at `wss://ws-feed.exchange.coinbase.com` with auto-reconnect
- **Wikimedia**: Server-Sent Events stream for recent changes

### PostgreSQL (File Uploads)
- JDBC connection for inserting file upload records
- Creates `file_uploads` table if not exists
- Works with Debezium for CDC event capture

## Key Files

| File | Purpose |
|------|---------|
| `Main.kt` | CLI subcommand routing (Clikt) |
| `Config.kt` | Environment-based configuration |
| `Producer.kt` | Kafka producer with Schema Registry |
| `MessageSender.kt` | Interface for Kafka message sending (testability) |
| `WebVisitGenerator.kt` | Synthetic data generation |
| `CidrRegistry.kt` | Country IP range loading |
| `TrafficSimulator.kt` | Realistic timing patterns |
| `FileUploadsConnector.kt` | PostgreSQL CDC data generator |
