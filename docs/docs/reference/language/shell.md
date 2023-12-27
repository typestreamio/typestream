# Shell commands

Since `TypeStream` is heavily inspired by the Unix philosophy, it's only natural
that it supports many shell commands.

:::note

Feature parity with Unix shells is a non-goal of `TypeStream` so do not expect
commands to be exactly the same as their Unix counterparts.

`TypeStream` also supports a few commands that are not available in Unix shells.

:::

## Cd

## History

### Synopsis

`history [-p]`

### Description

The `history` command is used to display the history of commands executed in the current session.

The following options are supported:

- `-p` `--print-session` - prints the history in a copy-paste friendly format

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
