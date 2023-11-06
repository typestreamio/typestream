# Data Operators

The purpose of this document is to spec `TypeStream` data operators.

## Cat

## Cut

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

## Enrich

## Join

## Wc
