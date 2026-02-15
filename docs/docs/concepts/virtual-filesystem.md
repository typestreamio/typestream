# Virtual Filesystem

TypeStream provides a UNIX-like virtual filesystem that maps Kafka infrastructure to a familiar directory structure. Topics become paths, schemas become types, and standard commands like `ls`, `cd`, and `cat` work as expected.

## Directory structure

```
/dev/kafka/local
├── brokers           # Kafka broker information
├── consumer-groups   # Consumer group metadata
├── schemas           # Schema Registry schemas
└── topics            # Kafka topics (this is where your data lives)
    ├── web_visits
    ├── crypto_tickers
    ├── wikipedia_changes
    ├── dbserver.public.file_uploads   # CDC topics from Debezium
    └── dbserver.public.orders
```

## Navigating the filesystem

In the interactive shell:

```sh
# List topics
ls /dev/kafka/local/topics

# Change directory
cd /dev/kafka/local/topics

# Read from a topic
cat web_visits
```

## Schema Registry integration

When you reference a topic path, TypeStream looks up its schema from Schema Registry automatically. This is how the system knows the field names and types for each topic -- enabling typed pipelines, schema validation, and field-based filtering.

For example, `/dev/kafka/local/topics/web_visits` might resolve to:

```
Struct[ip_address: String, url_path: String, status_code: Int, http_method: String, ...]
```

This schema information flows through the entire pipeline via [schema propagation](schema-propagation.md).

## How all three interfaces use the filesystem

The virtual filesystem is the common abstraction across all interfaces:

- **CLI DSL**: Topic paths are used directly in commands (`grep /dev/kafka/local/topics/web_visits [.status_code == 200]`)
- **Config-as-code**: The `topicPath` field in pipeline JSON files references filesystem paths
- **GUI**: The source node dropdown is populated by calling `FileSystemService.Ls` over gRPC

## Topic discovery

TypeStream periodically scans the Kafka cluster to discover new topics and update its filesystem. The refresh interval is configurable via `fsRefreshRate` in `typestream.toml` (default: 60 seconds).

When Debezium CDC is active, new topics appear automatically as tables are added to the source database.
