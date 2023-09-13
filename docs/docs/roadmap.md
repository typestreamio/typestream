# Roadmap

A loose outlook of where `TypeStream` is headed.

## Short term

- Extensible scheduler interface: we want to allow users to choose where to run long running jobs.
- Language
  - introduce functions and expression statements like:
    - `answers=$(cat questions | grep answers); grep $answers 42 > result_42; grep $answers 24 > result_24`
- Commands (these imply supporting more types)
  - find
  - tr
- Serialization
  - support for `protobuf`
  - Add JSON schema encoding support
  - write back to the schema registry

## Medium term

- Introduce new sources:
  - pulsar
  - postgresql
- Introduce naming strategies for: graph nodes, `TypeStream` apps.

## Long term

- Introduce a [job planner](#job-planner).
- Virtual filesystem
  - Introduce setting for displaying topics with conventions (vs code uses
    patterns for folders and maybe it's more flexible). It has to be cluster
    specific.
  - `/media` filesystem so we can expose data like `/media/websocket/server1`

### Job planner

It would be interesting to have a streaming job planner. Here's an example:

```sh
grep topic answer > result # first job
grep topic answer > another.topic # second job
```

obviously it feels like we can reuse `grep topic answer`. Can we? If so, the
planner would be responsible to answer this question.
