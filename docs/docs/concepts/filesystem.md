# File system

`TypeStream` provides a virtual file system.

At the root directory, we have the following directories:

```ssh
/dev/kafka/local
├── apps
├── brokers
├── consumer-groups
└── topics
```

At the moment the directory structure is Kafka specific because `TypeStream` only
supports Kafka. We will revise the structure once we support other
[sources](reference/glossary.md#source).
