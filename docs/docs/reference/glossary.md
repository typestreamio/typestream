# Glossary

Key terms used throughout the TypeStream documentation.

## CDC (Change Data Capture)

A technique for streaming database row-level changes (inserts, updates, deletes) into Kafka topics in real time. TypeStream uses [Debezium](https://debezium.io/) for CDC. See [CDC and Debezium](../concepts/cdc-and-debezium.md).

## Connector

A Kafka Connect plugin that moves data between Kafka and external systems. TypeStream uses **source connectors** (Debezium for CDC) and **sink connectors** (JDBC, Weaviate, Elasticsearch) to integrate with databases and search indexes.

## Edge

A connection between two nodes in a pipeline graph. Edges define the data flow direction from upstream to downstream.

## KeyValue

A record consisting of a key and a value. Kafka records are key-value pairs, and TypeStream preserves this structure throughout the pipeline.

## Node

A processing unit in a pipeline graph. Each node has a type (source, transform, enrichment, aggregation, or sink) and specific configuration. See the [Node Reference](node-reference.md).

## Pipeline

A directed acyclic graph of nodes and edges that defines a data processing flow. Pipelines are compiled into Kafka Streams topologies for execution.

## Record

Structured data that flows through streams in TypeStream. Records are typed -- their schema is resolved from Schema Registry and propagated through the pipeline at compile time.

## Schema Registry

A service that stores and serves Avro, JSON, and Protobuf schemas for Kafka topics. TypeStream uses Schema Registry to resolve topic schemas at compile time for type checking and schema propagation.

## Sink

A destination where pipeline output is written. Sinks can be Kafka topics, databases (via JDBC), vector databases (Weaviate), or search indexes (Elasticsearch).

## Source

A data platform that TypeStream reads from. Currently limited to Kafka clusters. Each [configured](configuration.md) source appears as a directory in the [virtual filesystem](../concepts/virtual-filesystem.md).

## Stream

A filesystem-addressable source of data. In TypeStream, Kafka topics are represented as streams in the virtual filesystem (e.g. `/dev/kafka/local/topics/books`).

## Topology

A Kafka Streams processing graph. TypeStream compiles pipeline definitions into topologies that run as continuous streaming applications.
