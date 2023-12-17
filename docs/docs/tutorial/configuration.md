# Configuration

`TypeStream` employs a configuration strategy we call _graceful configuration_.

The idea is that, by default, you do not need to configure anything: every
feature has defaults.

`TypeStream` configuration is done via a `server.properties` file.

In the following sections, we describe the available configuration options.

Required configuration are marked **bold**.

## Sources

This section of the configuration file is used to configure
[sources](reference/glossary.md#source). For each source type, you must provide
a comma-separated list of source names. For example:

```properties
sources.kafka=source1,source2
sources.http=source3
```

Then you can configure each source individually. For example:

```properties
sources.kafka.source1.bootstrapServers=localhost:9092
sources.kafka.source2.bootstrapServers=localhost:9092
sources.http.source3.port=8080
```

### Kafka

The following configuration options are available for Kafka sources:

| Name                      | Description                                                                                                    | Default                 |
| ------------------------- | -------------------------------------------------------------------------------------------------------------- | ----------------------- |
| **`bootstrapServers`**    | A comma-separated list of host:port pairs to use for establishing the initial connection to the Kafka cluster. | `localhost:9092`        |
| **`schemaRegistry.url`**  | The URL of the schema registry.                                                                                | `http://localhost:8081` |
| `schemaRegistry.userInfo` | The user info to use for authenticating with the schema registry.                                              |                         |
| `sasl.mechanism`          | The SASL mechanism to use for authenticating with the Kafka cluster.                                           | `PLAIN`                 |
| `sasl.jaasConfig`         | The JAAS configuration to use for authenticating with the Kafka cluster.                                       |                         |
