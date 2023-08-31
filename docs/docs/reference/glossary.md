# Glossary

An overview of the technical vocabulary that `TypeStream` documentation uses.

## KeyValue

## Record

`record` is how we call structured data that flows through [streams](#stream) in
`TypeStream`.

## Source

A `source` represents a unique data platform (at the moment, it can only be a
Kafka cluster) that `TypeStream` operates on.

For each [configured](tutorial/configuration.md) source, there will be an equivalent
entry in the [virtual filesystem](concepts/filesystem.md).

## Stream

A `stream` is an filesystem addressable source of data. `TypeStream` operates on streams

## Types

- String
- Avro
- JSON
- protocol buffer
- number
