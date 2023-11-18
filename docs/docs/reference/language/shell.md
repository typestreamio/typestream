# Shell commands

Since `TypeStream` is heavily inspired by the Unix philosophy, it's only natural
that it supports many shell commands.

:::note
Note that feature parity with Unix shells is not a goal of `TypeStream` so do not expect commands to be exactly the same as their Unix counterparts.

Furthermore, `TypeStream` supports a few commands that are not available in Unix shells.
:::

## Cd

## Http

### Synopsis

`http [verb] <url> [data]`

### Description

The `http` command is used to make requests. It supports the following verbs:

- `GET`, `get` - default if no verb is specified
- `POST`, `post` - requires `data` to be specified

The `data` argument must be a string.

Note that the `http` command **only** supports JSON requests and responses. In fact,
it will automatically set the `Content-Type` header to `application/json` and
parse the response as JSON.

## Ls

## Pwd

## Ps
