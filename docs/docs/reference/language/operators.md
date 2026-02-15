# Data Operators

Data operators transform, filter, and process records flowing through a TypeStream pipeline. They are chained together using the pipe (`|`) operator.

## Cat

### Synopsis

`cat <path>`

### Description

The `cat` operator reads records from a data stream and outputs them. It is typically the first operator in a pipeline.

```sh
cat /dev/kafka/local/topics/books
```

## Cut

### Synopsis

`cut <field1> [<field2> ...]`

### Description

The `cut` operator selects specific fields from each record, producing a new record with only the named fields.

```sh
cat /dev/kafka/local/topics/books | cut .title .author_id
```

## Each

### Synopsis

`each <block expression>`

### Description

The `each` data operator is used to execute a block expression for each record in a data stream. The block expression is required.

`each` **must** be the last operator in a pipeline.

Here's an example of using `each` to make a HTTP request for each record in a data stream:

```sh
cat /dev/kafka/local/topics/books | each { book -> http post https://example.com/new_books "{\"book_id\": #{$book.id}}" }
```

## Enrich

### Synopsis

`enrich <block expression>`

### Description

The `enrich` data operator is used to enrich data streams. The block expression is required. `enrich` **cannot** be the first operator in a pipeline.

`enrich` evaluates the block expression for each record in the data stream and will merge the result with the original record, therefore enriching it.

```sh
cat /dev/kafka/local/topics/web_visits | enrich { visit -> http "https://api.country.is/#{$visit.ip_address}" }
```

## Grep

### Synopsis

`grep [-kv] [<pattern>|[<predicate>]] [<path>]`

### Description

The `grep` data operator is used to filter data streams. The default string pattern usage filters by "content" (i.e. the whole record) matching each record against the pattern.

For more complex filter operations, grep supports predicate expressions. See the [predicate expressions](#predicate-expressions) section for more details.

The following options are supported:

- `-k` `--by-key` - filter by key
- `-v` `--invert-match` - invert the sense of matching, to select non-matching records

#### Predicate expressions

:::note

While in the rest of this document the characters `[` and `]` indicate
optional parts of a command, here they are part of the syntax and must be
included in the expression.

:::

`[ .field <operator> <value> ]`

Expressions can be combined with `&&` and `||` operators and grouped with `()`.

Here's the list of supported operators:

- `==`
  Strict equality operator. The value must be of the same type as the field.
- `!=`
  Strict inequality operator. The value must be of the same type as the field.
- `>`, `>=`, `<`, `<=`
  Numeric comparison operators. The value must be a number.
- `~=`
  "Contains" operator. It matches if the field contains the value. Ignores case.

See the [filter and route](/how-to/filter-and-route) how-to guide for examples.

## Join

### Synopsis

`join <path1> <path2>`

### Description

The `join` operator joins two data streams by key. Records with matching keys from both streams are merged into a single output record containing fields from both sides.

```sh
join /dev/kafka/local/topics/orders /dev/kafka/local/topics/users > /dev/kafka/local/topics/orders_enriched
```

The output encoding defaults to JSON since the schema differs from either input.

## Wc

### Synopsis

`wc`

### Description

The `wc` (word count) operator counts records per key. It is typically used as the last operator in a pipeline to produce aggregation counts.

```sh
cat /dev/kafka/local/topics/web_visits | wc
```
