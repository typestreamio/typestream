# Data Catalog

The `TypeStream` data catalog is a key-value store where:

- The key is a path to a data stream. Examples: `/dev/kafka/local/topics/users`,
  `/dev/dataflow/cluster1/topics/clicks`.
- The value is a [DataStream](reference/language/spec.md#data-stream).

A DataStream holds:

- Its reference path (which is used to derive identifiers)
- Schema information. For example, `/dev/kafka/local/topics/users` may be a
  `Struct[id: String, name: String, createdAt: Date]`

The compiler uses the data catalog to "type check" source code.

The data catalog is also used to determine the output type of streaming
operations that involve more than one DataStream. See the [DataStream
type](reference/language/spec.md#data-stream) documentation for more
information.

## Encoding rules

`TypeStream` needs to distinguish between different data stream types and their
encodings. The former is part of the typing system of the language, while the
latter is relevant when reading data from sources and writing data back.

Here are the rules that determine the output data stream encoding:

- If the output data stream type is the same as the input data stream type, the
  output data stream encoding is the same as the input data stream encoding.
- If the output data stream type is different from the input data stream type,
  then we default to JSON encoding.

Consider the following data streams:

```sh
let authors = "/dev/kafka/local/topics/authors" # Struct[id: String, name: String] encoded as Avro
let books = "/dev/kafka/local/topics/books" # Struct[id: String, title: String] encoded as Avro
let ratings = "/dev/kafka/local/topics/ratings" # Struct[bookId: String, userId: String, rating: Int] encoded as JSON
```

The following pipeline:

```sh
cat books | grep "Station eleven" > station_books
```

will be encoded as Avro since:

- The input data stream type is Avro encoded.
- The output data stream type is the same as the input one.

While the following pipeline:

```sh
cat books | cut title > book_titles
```

will be encoded as JSON since:

- The input data stream type is Avro encoded.
- The output data stream type is different from the input one.

Also the following pipeline:

```sh
join books ratings > book_ratings
```

will be encoded as JSON since:

- One input data stream type is Avro encoded.
- One input data stream type is JSON encoded.
- The output data stream type is different from the input one.
