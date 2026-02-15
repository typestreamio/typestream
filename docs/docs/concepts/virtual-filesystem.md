# Virtual Filesystem

TypeStream provides a UNIX-like virtual filesystem that maps Kafka infrastructure to a familiar directory structure. Topics become paths, schemas become types, and standard commands like `ls`, `cd`, and `cat` work as expected.

## Directory structure

```
/dev/kafka/local
├── apps              # Running Kafka Streams applications
├── brokers           # Kafka broker information
├── consumer-groups   # Consumer group metadata
└── topics            # Kafka topics (this is where your data lives)
    ├── books
    ├── authors
    ├── ratings
    ├── users
    ├── demo.public.orders      # CDC topics from Debezium
    └── demo.public.users
```

## Navigating the filesystem

In the interactive shell:

```sh
# List topics
ls /dev/kafka/local/topics

# Change directory
cd /dev/kafka/local/topics

# Read from a topic
cat books
```

## Schema Registry integration

When you reference a topic path, TypeStream looks up its schema from Schema Registry automatically. This is how the system knows the field names and types for each topic -- enabling typed pipelines, schema validation, and field-based filtering.

For example, `/dev/kafka/local/topics/books` might resolve to:

```
Struct[id: String, title: String, word_count: Int, author_id: String]
```

This schema information flows through the entire pipeline via [schema propagation](schema-propagation.md).

## How all three interfaces use the filesystem

The virtual filesystem is the common abstraction across all interfaces:

- **CLI DSL**: Topic paths are used directly in commands (`grep /dev/kafka/local/topics/books "Station"`)
- **Config-as-code**: The `dataStream.path` field in pipeline JSON files references filesystem paths
- **GUI**: The source node dropdown is populated by calling `FileSystemService.Ls` over gRPC

## Topic discovery

TypeStream periodically scans the Kafka cluster to discover new topics and update its filesystem. The refresh interval is configurable via `fsRefreshRate` in `typestream.toml` (default: 60 seconds).

When Debezium CDC is active, new topics appear automatically as tables are added to the source database.
