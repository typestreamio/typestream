---
slug: /
---

# Getting started

:::info

At the moment, `TypeStream` can only operate on streaming data via Kafka topics.
Check out the [roadmap](roadmap.md) for more information about upcoming plans.

:::

## What is TypeStream?

`TypeStream` is an abstraction layer on top of Kafka that allows you to write
and run _typed_ data pipelines with a minimal, familiar syntax. It borrows its
core ideas from the UNIX philosophy where everything is a file. In `TypeStream`,
everything is a typed data stream.

In these few sentences, there's a lot to unpack so we'll take it slow and go
over each concept one at the time. By the end of this tutorial, you will know:

- How `TypeStream` can achieve so much with such a minimal syntax.
- How to interact with it you so you can start writing your own pipelines.

:::info

If you want to follow along, make sure you [install](tutorial/installation.md)
`TypeStream` locally.

:::

## Run TypeStream locally

To help you get started, we created a few `local` commands to help you run a
local (surprise 😄) `TypeStream` server.

You can start it by running:

```bash
typestream local start
```

You should see something like this:

```bash
2023/06/27 10:19:33 INFO 🚀 starting TypeStream server
2023/06/27 10:19:33 INFO 🛫 starting server
2023/06/27 10:19:33 INFO 🛫 starting redpanda
2023/06/27 10:19:33 INFO ✨ redpanda started
2023/06/27 10:19:33 INFO ✨ server started
2023/06/27 10:19:34 INFO ✅ server healthy
2023/06/27 10:19:39 INFO ✅ redpanda healthy
2023/06/27 10:19:39 INFO 🎉 TypeStream server started
```

## Create your first pipeline

We created a small dataset that you can use to play around with `TypeStream`.
You can seed your cluster with it by running:

```bash
typestream local seed
```

You should see something like this:

```bash
2023/06/27 10:19:42 INFO 📥 pulling image
2023/06/27 10:19:42 INFO ⏳ this may take a while...
2023/06/27 10:19:43 INFO ⛽ starting seeding process
2023/06/27 10:19:49 INFO 🎉 seeding successful
2023/06/27 10:19:49 INFO 🗑️  deleting container
2023/06/27 10:19:49 INFO ✅ done
```

This will create a few topics in your Kafka cluster and populate them with some
data.

Now you're ready to start writing your first pipeline but first let's make sure
that your cluster is correctly set up:

```bash
typestream run 'ls /dev/kafka/local/topics'
```

If you see something like this:

```bash
_schema
authors
books
ratings
users
```

then you're all set. You're now ready to start writing your first pipeline 🚀🚀

## Hello, streams

Imagine you have a local Kafka cluster which contains a few topics about a books
social network you've been working on.

You don't remember exactly how exactly the topics are called so you fire up a `TypeStream`
[shell](concepts/components.md#shell) and type the following:

```sh
$ typestream
>
```

If you see that `>` then your `TypeStream` shell is ready to run your commands.

Paste the following one liner to check if the book "Station eleven" is in the
books topic:

```sh
cat /dev/kafka/local/topics/books | grep "Station eleven"
```

You should see something like:

```text
{"id":"b1fb542c-2e02-4db8-bcb9-e12b9dff21fd","title":"Station Eleven","word_count":"300" "author_id":"386428f9-8ad2-4011-8199-b2674d671f87"}
```

Since data pipelines are unbound, the command will run indefinitely (or until you stop it with `Ctrl+C`).

## What did we learn?

These two short examples tell us a lot about `TypeStream`:

- The syntax is familiar to UNIX users. At first sight, it looks a bit like bash.
- `TypeStream` data pipelines are _typed_. In the second example, you can see that the
  output is a structured JSON object derived from the original Avro encoded
  "books" topic.
- You build data pipelines by chaining commands with pipes.

In the examples we've seen so far, `TypeStream` outputted the data to the
standard output which may come handy with small topics or in debugging
scenarios. However, in most cases, you will want to output the results of your
data pipelines to a new topic. The syntax is as familiar as it can get:

```sh
cat /dev/kafka/local/topics/ratings | grep "386428f9-8ad2-4011-8199-b2674d671f87"  > /dev/kafka/local/topics/mandel_ratings
```

## Where to go from here?

We will be adding more tutorials in the near future but in the meantime, reach
out to us with any questions you may have. We'd love to hear from you!

If you're curious about the internals of `TypeStream`, check out the
[components](concepts/components.md) page.
