# Specification

:::warning

This document is a work in progress. It's not complete and it's not final.

:::

The purpose of this document is to spec `TypeStream` language features.

While `TypeStream` syntax is inspired by
[bash](https://tiswww.case.edu/php/chet/bash/bashtop.html), feature parity is a
_non_ goal of the project.

## Keywords

TODO

## Types

By default, `TypeStream` doesn't need a syntax to specify types. It can infer
types from context.

- String
- Number
- List
- DataStream

### DataStream

In `TypeStream`, a DataStream is a typed source of data. `TypeStream` always
outputs structured data (defaulting to encoding data in JSON).

Let's start from some examples:

```sh
# words is a "string" stream, meaning each message value is a string

$ cat words # 1,"first word" 2,"second word"

# users is a "record" topic, meaning each message value is structured record (can be avro, json, protobuf, etc..)

$ cat users # 1, [name: Foo, age: 42] 2, [name: Bar, age: 24]

# what happens if we join users and words?

$ join users words

# 1, [users: {name: Foo, age: 42}, words: "first word"]
# 2, [users: {name: Foo, age: 42}, words: "second word"]
```

#### Merging data streams

## Block expression

Block expressions define anonymous functions that take one parameter. They're
used by `TypeStream` data operators like `each` and `enrich`.

Here's their syntax:

```js
{ <var> -> <pipeline> }
```

and an example using the `each` operator:

```sh
cat users | each { user -> echo $user }
```

In the example above, the `user` variable is bound to each record in the `users` data stream.
