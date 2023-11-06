---
description: Learn how to filter streams using the grep operator.
---

# Filtering streams

The purpose of this document is to explain how to use the `grep` operator via
examples. If you're looking for a formal specification of its usage, refer to
the [specification](reference/language/operators.md#grep) document.

## Filtering by content

The simplest way to do filtering is to use the `grep` operator with a string
parameter:

```sh
grep /dev/kafka/local/topics/books "Station"
{"id":"9923d491-d421-4c84-9a97-7d1f7bc613a4","title":"Station Eleven","word_count":300,"author_id":"011bb70f-9dd3-4a1e-894c-bf6bb19879f8"}
```

bare words are also supported, meaning that the following is equivalent to the previous example:

```sh
grep /dev/kafka/local/topics/books station
```

Note that the `grep` data operator is case _insensitive_ because it is meant for
quick searches so it's not the most flexible.

For more complex filtering, you can use the `grep` operator with a predicate
expression as explained in the rest of this document.

## Filtering by field

The `grep` data operator supports predicate expressions. They allow you to
express more complex filtering conditions. See the
[reference](reference/language/operators.md#predicate-expressions) for a formal
specification of its syntax.

The following example shows how to filter by a specific field:

```sh
grep /dev/kafka/local/topics/books [.word_count > 250]
{"id":"76b47cb7-07cc-4949-a178-2e3acfbbf1f1","title":"The Glass Hotel","word_count":500 "author_id":"743626be-8380-40e9-ab1b-44dfc398cde0"}
{"id":"392ff175-6f93-4228-8620-fba20b6a4f73","title":"Station Eleven","word_count":300,"author_id":"743626be-8380-40e9-ab1b-44dfc398cde0"}
{"id":"9ba6893e-2980-4316-b9c3-605799a08bde","title":"Sea of Tranquility","word_count":400,"author_id":"743626be-8380-40e9-ab1b-44dfc398cde0"}
{"id":"a8c6a266-2827-4b87-873a-f1fcf3f9b138","title":"Purple Hibiscus","word_count":300,"author_id":"da68bea8-4a8e-4f96-bc39-25b0b697d94b"}
```

You can also combine multiple conditions:

```sh
grep /dev/kafka/local/topics/books [ .word_count == 300 || .title ~= 'the' ]
{"id":"3734cb2a-6ba3-4227-9e3a-447df83e5818","title":"Station Eleven","word_count":300,"author_id":"9a48dc54-ece4-4be9-ae08-c8c93d6bde5a"}
{"id":"8dae24e8-20e4-46ef-8465-cf8100b41c75","title":"Parable of the Talents","word_count":200,"author_id":"46fdbf84-b7b1-4dc5-9eb7-f6024fc5725f"}
{"id":"809c833e-b491-49a9-a0fb-cc0f263238c0","title":"The Glass Hotel","word_count":500,"author_id":"9a48dc54-ece4-4be9-ae08-c8c93d6bde5a"}
{"id":"c38b7288-ee8c-4708-bc2f-2dc1dff28c2a","title":"Parable of the Sower","word_count":200,"author_id":"46fdbf84-b7b1-4dc5-9eb7-f6024fc5725f"}
{"id":"6f0c405e-9de4-44cd-a7da-e28462ddcf14","title":"Purple Hibiscus","word_count":300,"author_id":"37d75835-f131-4a7a-99c4-fe9d54703c05"}
```
