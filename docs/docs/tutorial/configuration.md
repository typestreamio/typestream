# Configuration

`TypeStream` is configured using a [TOML](https://toml.io/en/) file.

At startup, `TypeStream` searches for a `typestream.toml` file using the
`TYPESTREAM_CONFIG_PATH` environment variable. If it finds one, it uses it to
configure itself. If it does not find one, it uses the following defaults:

```toml
[grpc]
port = 4242
[sources.kafka.local]
bootstrapServers = "localhost:9092"
schemaRegistry.url = "http://localhost:8081"
fsRefreshRate = 60
```

The `TYPESTREAM_CONFIG_PATH` environment variable is a colon-separated list of
paths to search for a `typestream.toml` file. If the environment variable is not
set, `TypeStream` searches for a `typestream.toml` file in the following
locations:

1. The current working directory.
2. The `/etc/typestream` directory.

You can also specify a configuration via the environment variable
`TYPESTREAM_CONFIG`.

In the following sections, we describe the available configuration options.

Required configuration is marked **bold**.

## Grpc

The following configuration options are available for the gRPC server:

| Name       | Description                                    | Default |
| ---------- | ---------------------------------------------- | ------- |
| **`port`** | The port on which to listen for gRPC requests. | `4242`  |

## Sources

This section of the configuration file is used to configure
[sources](reference/glossary.md#source).

### Kafka

The following configuration options are available for Kafka sources:

| Name                      | Description                                                                                                    | Default                 |
| ------------------------- | -------------------------------------------------------------------------------------------------------------- | ----------------------- |
| **`bootstrapServers`**    | A comma-separated list of host:port pairs to use for establishing the initial connection to the Kafka cluster. | `localhost:9092`        |
| **`schemaRegistry.url`**  | The URL of the schema registry.                                                                                | `http://localhost:8081` |
| `schemaRegistry.userInfo` | The user info to use for authenticating with the schema registry.                                              |                         |
| `sasl.mechanism`          | The SASL mechanism to use for authenticating with the Kafka cluster.                                           | `PLAIN`                 |
| `sasl.jaasConfig`         | The JAAS configuration to use for authenticating with the Kafka cluster.                                       |                         |
