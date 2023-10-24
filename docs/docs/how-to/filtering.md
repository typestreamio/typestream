---
description: Learn how to filter streams using the grep operator.
---

# Filtering streams

In the true spirit of Unix, `TypeStream` allows you to filter streams using the
`grep` data operator.

## Filtering by content

The simplest way to do filtering is to use the `grep` operator with a string parameter:

```bash
cat /dev/kafka/local/topics/books | grep "Station"
{"id":"9923d491-d421-4c84-9a97-7d1f7bc613a4","title":"Station Eleven","word_count":300,"author_id":"011bb70f-9dd3-4a1e-894c-bf6bb19879f8"}
```

Here's this example in action:

![grep](../../../assets/vhs/grep.gif)

## Filtering by field

You can also filter by field using the `grep` operator with a field name parameter:

```bash
 cat /dev/kafka/local/topics/books | grep [.word_count > 250]
{"id":"76b47cb7-07cc-4949-a178-2e3acfbbf1f1","title":"The Glass Hotel","word_count":500,"author_id":"743626be-8380-40e9-ab1b-44dfc398cde0"}
{"id":"392ff175-6f93-4228-8620-fba20b6a4f73","title":"Station Eleven","word_count":300,"author_id":"743626be-8380-40e9-ab1b-44dfc398cde0"}
{"id":"9ba6893e-2980-4316-b9c3-605799a08bde","title":"Sea of Tranquility","word_count":400,"author_id":"743626be-8380-40e9-ab1b-44dfc398cde0"}
{"id":"a8c6a266-2827-4b87-873a-f1fcf3f9b138","title":"Purple Hibiscus","word_count":300,"author_id":"da68bea8-4a8e-4f96-bc39-25b0b697d94b"}
```
