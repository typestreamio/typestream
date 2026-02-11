<div align="center">
    <img src="/assets/avatar-transparent.png?raw=true" width="86">
</div>

<h1 align="center">TypeStream</h1>

<br />

<div align="center">
    <a href="https://github.com/typestreamio/typestream/blob/main/LICENSE">
        <img src="https://img.shields.io/badge/license-BSL%201.1-blue" />
    </a>
    <a href="https://discord.gg/Ha9sJWXb">
        <img src="https://img.shields.io/badge/Chat-on%20Discord-blue" alt="Discord invite" />
    </a>
</div>

<p align="center">
    <a href="#getting-started">Getting started</a>
    ·
    <a href="#development">Development</a>
    ·
    <a href="#how-to-contribute">How to contribute</a>
    ·
    <a href="#license">License</a>
</p>

<h3 align="center">

TypeStream connects to your Postgres or MySQL and turns every insert, update,
and delete into a real-time pipeline — syncing data, enriching it with AI, and
exposing it instantly.

</h3>

![Building a pipeline with TypeStream](/assets/images/hero-demo.gif?raw=true)

![Materialized API with live crypto prices](/assets/images/crypto-demo.gif?raw=true)

## Getting Started

### Prerequisites

- [Docker](https://docs.docker.com/get-docker/) and Docker Compose

### Start TypeStream

```sh
git clone https://github.com/typestreamio/typestream.git
cd typestream

# Copy environment template and customize
cp .env.example .env

# Start TypeStream
docker compose up -d
```

Open the TypeStream UI at **http://localhost** and start building pipelines.

### Start Demo Data

To explore TypeStream with live data, add the demo overlay:

```sh
docker compose -f docker-compose.yml -f docker-compose.demo.yml up -d
```

This starts four data generators that produce a continuous stream of events:

| Generator | What it does |
|-----------|-------------|
| **Coinbase** | Real-time BTC-USD and ETH-USD crypto prices via the Coinbase WebSocket API |
| **Wikipedia** | Live edit events from the English Wikipedia recent changes stream |
| **Web Visits** | Synthetic page-view events with IP addresses, user agents, and paths |
| **File Uploads** | Inserts sample documents into Postgres (captured via Debezium CDC) |

### Configuration

Edit `.env` to customize your deployment:

```sh
# Image version (defaults to latest)
TYPESTREAM_VERSION=latest

# External ports
UI_PORT=5173
KAFKA_EXTERNAL_PORT=19092
SCHEMA_REGISTRY_PORT=18081
ENVOY_PORT=8080
KAFBAT_PORT=8088

# Optional: enable AI features
OPENAI_API_KEY=your-key-here
```

### Access Points

| Service | URL |
|---------|-----|
| TypeStream UI | http://localhost |
| Kafbat (Kafka UI) | http://localhost:8088 |
| Kafka Bootstrap | localhost:19092 |
| Schema Registry | http://localhost:18081 |
| gRPC-Web (Envoy) | http://localhost:8080 |

### Stop Services

```sh
# If running with demo data
docker compose -f docker-compose.yml -f docker-compose.demo.yml down

# If running base only
docker compose down

# Remove volumes for a fresh start
docker compose down -v
```

## Development

For contributing to TypeStream, the dev overlay swaps the UI for a hot-reload
version and builds services from source.

### Prerequisites

- [Docker](https://docs.docker.com/get-docker/) and Docker Compose
- [Nix](https://nixos.org/download.html) (for building the server)

### Setup

```sh
# Enter the Nix dev shell
nix develop

# Start infrastructure services (Redpanda, Envoy, UI, Kafka Connect, etc.)
docker compose -f docker-compose.yml -f docker-compose.dev.yml up -d

# Run the server on your host with hot reload
./scripts/dev/server.sh
```

Edit Kotlin files and watch them auto-reload in ~5 seconds.

The UI is available at **http://localhost:5173** with hot reload. Demo data
generators (Coinbase, Wikipedia, web visits, file uploads) are included in the
dev overlay and start automatically.

### Stop Services

```sh
docker compose -f docker-compose.yml -f docker-compose.dev.yml down
```

### Releasing

To create a new release:

1. Push a version tag (e.g., `v1.0.0`):
   ```sh
   git tag v1.0.0
   git push origin v1.0.0
   ```

2. The CI workflow will automatically:
   - Build and smoke test all Docker images (server, demo-data, kafka-connect)
   - Publish the images to GitHub Container Registry (ghcr.io)
   - Create a GitHub Release with placeholder notes

3. After the release is created, edit the release notes in the GitHub UI to add
   details about the changes.

## How to Contribute

We love every form of contribution! Good entry points to the project are:

- Our [contributing guidelines](/CONTRIBUTING.md) document.
- Issues with the tag
  [gardening](https://github.com/typestreamio/typestream/issues?q=is%3Aissue+is%3Aopen+label%3Agardening).
- Issues with the tag [good first
  patch](https://github.com/typestreamio/typestream/issues?q=is%3Aissue+is%3Aopen+label%3A%22good+first+patch%22).

If you're not sure where to start, open a [new
issue](https://github.com/typestreamio/typestream/issues/new) or hop on to our
[discord](https://discord.gg/Ha9sJWXb) server and we'll gladly help you get
started.

## Code of Conduct

You are expected to follow our [code of conduct](/CODE_OF_CONDUCT.md) when
interacting with the project via issues, pull requests, or in any other form.
Many thanks to the awesome [contributor
covenant](http://contributor-covenant.org/) initiative!

## License

TypeStream is licensed under the [Business Source License 1.1](/LICENSE) (BSL).

### Why BSL?

We're a small team building TypeStream in the open. We chose the Business Source License because it lets us:

- **Stay transparent**: You can read, audit, and learn from every line of code
- **Enable adoption**: You can freely use TypeStream for internal projects, development, testing, and non-commercial use
- **Protect our work**: The BSL prevents large cloud providers from offering TypeStream as a competing service without contributing back
- **Commit to open source**: After 4 years, each version automatically converts to Apache 2.0

**What this means for you:**
- ✅ Use TypeStream internally at your company
- ✅ Modify and extend it for your own needs  
- ✅ Contribute back to the project
- ❌ Offer TypeStream as a managed streaming service to third parties (without a commercial license)

If you're building a product on TypeStream or have questions about licensing, reach out to us at licensing@typestream.io.

Companies like [MariaDB](https://mariadb.com/bsl11/), [CockroachDB](https://www.cockroachlabs.com/blog/oss-relicensing-cockroachdb/), [Sentry](https://blog.sentry.io/introducing-the-functional-source-license-freedom-without-free-riding/), and [HashiCorp](https://www.hashicorp.com/license-faq) use similar licenses to balance openness with sustainability.
