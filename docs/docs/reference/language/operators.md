# Data Operators

The purpose of this document is to spec `TypeStream` data operators.

## Cat

## Cut

## Each

### Synopsis

`each <block expression>`

### Description

The `each` data operator is used to execute a [block expression](spec.md#block-expression) for each record in a data stream. The
block expression is required.

`each` **must** be the last operator in a pipeline.

Here's an example of using `each` to make a HTTP request for each record in a data stream:

```sh
cat /dev/kafka/local/books | each { book -> http post https://example.com/new_books "{\"book_id\": #{$book.id}}" }
```

## Enrich

### Synopsis

`enrich <block expression>`

### Description

The `enrich` data operator is used to enrich data streams. The block expression
is required. `enrich` **cannot** be the first operator in a pipeline.

`enrich` evaluates the block expression for each record in the data stream and
will [merge](spec.md#merging-data-streams) the result with the original record
therefore enriching it.

See [enriching](how-to/enriching.md) for more examples.

## Grep

### Synopsis

`grep [-kv] [<pattern>|*(<predicate>)] [<path>]`

### Description

The `grep` data operator is used to filter in data streams. The default string
pattern usage filters by "content" (i.e. the whole record) matching each records
against the pattern.

For more complex filter operations, grep supports predicates expressions. See
the [predicate expressions](#predicate-expressions) section for more details.

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

See the [filtering streams](how-to/filtering.md) how-to document for examples.

## Join

## Wc
