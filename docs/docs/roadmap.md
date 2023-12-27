# Roadmap

A loose outlook of where `TypeStream` is headed.

There's no particular order to this list of lists at the moment. This reflects
the current state of ideas we may or may not implement in the near feature.

To get a better sense of the upcoming features, check out the
[issues](https://github.com/typestreamio/typestream/issues).

## Language

Refer to the [specs](reference/language/spec.md) for the current state of the language.

- introduce functions and expression statements like:
  - `answers=$(cat questions | grep answers); grep $answers 42 > result_42; grep $answers 24 > result_24`
- support for `*` expansion
- support list values (it's necessary for the `*` expansion and commands like find and tr)
- support for DataStream Values (commands like find and ls imply this)

## New commands

Since it's early days, some commands will require work at the language level but
we still have a separate section for them so it's clearer what commands we know
we want to support.

- find
- tr
- [tee](https://github.com/typestreamio/typestream/issues/78)
- mount (see [Filesystem](#filesystem))

## Filesystem

The virtual filesystem is a core component of `TypeStream`. It's how the
metaphor "everything is a stream" is implemented.

- `/media` filesystem (aka "mounting")
  - websocket server
  - http server
  - jdbc server
- There's no way to get the output of a long running program. We want to make
  this work [How to view the output of a running process in another bash
  session?](https://unix.stackexchange.com/questions/58550/how-to-view-the-output-of-a-running-process-in-another-bash-session)
  at filesystem level

## CI

We could always have more tests... right? 😃

- integration tests (defined as cli + server interactions. The whole stack only
  basically)
  - kubernetes features
    - config map
    - need to test the server and worker at integration level
  - This test suite must be fully integrated with the release process (as we
    want to run it on release branches automatically. Should also be possible to
    run manually or trigger in pull requests)

## Server

- Failure handling strategy
  - we have to restart watchers if they fail (retry until a certain threshold
    and then crash the server?)
  - if the catalog fails, we can't type check but the errors are unclear (as we
    just say we can't find the type)
  - Add an exception handler for kafka streams applications. You don't want them
    to blow up but to log stuff

## Encoding

- schema enum isn't really efficient (as it carries the type in each instance, we need a container type for this)
- [Avro](https://avro.apache.org)
  - support duration
  - some types metadata isn't carried over (see logical types with precision and scale for example)
- [Protocol Buffers](https://protobuf.dev/)
  - Introduce "SmokeType" first so we can support:
    - google imports
    - nested types
    - multiple messages in the same definition
- [json schema](https://json-schema.org)
- [cloud events](https://cloudevents.io)
