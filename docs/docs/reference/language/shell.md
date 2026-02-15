# Shell Commands

Since TypeStream is heavily inspired by the Unix philosophy, it supports many shell commands for navigating the virtual filesystem and managing jobs.

:::note

Feature parity with Unix shells is a non-goal of TypeStream so do not expect
commands to be exactly the same as their Unix counterparts.

TypeStream also supports a few commands that are not available in Unix shells.

:::

## Cd

### Synopsis

`cd [<path>]`

### Description

The `cd` command changes the current working directory in the virtual filesystem. Without arguments, it returns to the root directory.

```sh
cd /dev/kafka/local/topics
cat books  # equivalent to cat /dev/kafka/local/topics/books
```

## Env

### Synopsis

`env`

### Description

The `env` command displays all environment variables in the current session, sorted alphabetically.

```sh
env
```

## File

### Synopsis

`file <path>`

### Description

The `file` command displays type information about a path in the virtual filesystem.

```sh
file /dev/kafka/local/topics/books
```

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

## Kill

### Synopsis

`kill <program id>`

### Description

The `kill` command stops a running job by its program ID. Use `ps` to find the program ID.

```sh
kill abc123
```

## Ls

### Synopsis

`ls [<path>]`

### Description

The `ls` command lists the contents of a directory in the virtual filesystem. Without arguments, it lists the current directory.

```sh
ls /dev/kafka/local/topics
```

## Openai-complete

### Synopsis

`openai-complete <sentence>`

### Description

The `openai-complete` command sends a prompt to the OpenAI completions API and returns the result. Requires the `OPENAI_API_KEY` environment variable to be set on the server.

```sh
openai-complete "What is Kafka Streams?"
```

## Ps

### Synopsis

`ps`

### Description

The `ps` command lists all running jobs with their ID and status. Use this to monitor active pipelines.

```sh
ps
```

## Pwd

### Synopsis

`pwd`

### Description

The `pwd` command prints the current working directory in the virtual filesystem.

```sh
pwd
/dev/kafka/local/topics
```

## Stat

### Synopsis

`stat <path>`

### Description

The `stat` command displays detailed information about a path in the virtual filesystem.

```sh
stat /dev/kafka/local/topics/books
```
