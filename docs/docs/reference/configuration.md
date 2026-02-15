# Configuration

TypeStream is configured using a [TOML](https://toml.io/en/) file.

At startup, the server searches for a `typestream.toml` file using the `TYPESTREAM_CONFIG_PATH` environment variable. If not set, it searches:

1. The current working directory
2. The `/etc/typestream` directory

You can also set the full configuration via the `TYPESTREAM_CONFIG` environment variable.

## Default configuration

```toml
[grpc]
port = 4242

[sources.kafka.local]
bootstrapServers = "localhost:9092"
schemaRegistry.url = "http://localhost:8081"
fsRefreshRate = 60
```

## gRPC

| Name | Description | Default |
|------|-------------|---------|
| **`port`** | Port for gRPC requests | `4242` |

## Sources

### Kafka

Each Kafka source is configured under `[sources.kafka.<name>]`. The name becomes part of the virtual filesystem path (e.g. `local` -> `/dev/kafka/local/`).

| Name | Description | Default |
|------|-------------|---------|
| **`bootstrapServers`** | Comma-separated host:port pairs for the Kafka cluster | `localhost:9092` |
| **`schemaRegistry.url`** | Schema Registry URL | `http://localhost:8081` |
| `schemaRegistry.userInfo` | User info for Schema Registry authentication | |
| `sasl.mechanism` | SASL mechanism for Kafka authentication | `PLAIN` |
| `sasl.jaasConfig` | JAAS configuration for Kafka authentication | |
| `fsRefreshRate` | Virtual filesystem refresh interval in seconds | `60` |

Required fields are shown in **bold**.
