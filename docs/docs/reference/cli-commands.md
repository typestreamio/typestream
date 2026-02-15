# CLI Commands

Complete reference for the TypeStream CLI (`typestream`).

## Environment variables

| Variable | Default | Description |
|----------|---------|-------------|
| `TYPESTREAM_HOST` | `127.0.0.1` | TypeStream server hostname |
| `TYPESTREAM_PORT` | `4242` | TypeStream server gRPC port |

## Command tree

### typestream

When invoked with no subcommand, starts the interactive shell (REPL). When stdin is piped, executes the input as a one-shot program.

```bash
# Interactive mode
typestream

# One-shot mode
echo 'ls /dev/kafka/local/topics' | typestream
```

### typestream run

Create a streaming job from a DSL expression or file.

```bash
typestream run 'grep /dev/kafka/local/topics/web_visits [.status_code == 200] > /dev/kafka/local/topics/ok_visits'
typestream run pipeline.ts
```

### typestream apply

Apply a `.typestream.json` pipeline definition. Creates or updates the pipeline.

```bash
typestream apply my-pipeline.typestream.json
```

### typestream plan

Dry-run: show what `apply` would change. Accepts a single file or a directory.

```bash
typestream plan my-pipeline.typestream.json
typestream plan ./pipelines/
```

### typestream validate

Validate a `.typestream.json` file without applying it.

```bash
typestream validate my-pipeline.typestream.json
```

### typestream pipelines list

List all managed pipelines with their name, version, job ID, state, and description.

```bash
typestream pipelines list
```

### typestream pipelines delete

Delete a managed pipeline by name.

```bash
typestream pipelines delete my-pipeline
```

### typestream mount

Mount a data source endpoint.

```bash
typestream mount config.toml
```

### typestream unmount

Unmount a data source endpoint.

```bash
typestream unmount /dev/kafka/remote
```

### typestream local start

Start the full stack via Docker Compose (server + infrastructure).

```bash
typestream local start
```

### typestream local stop

Stop the full stack.

```bash
typestream local stop
```

### typestream local dev

Start infrastructure only (server runs on host for hot reload).

```bash
typestream local dev
```

### typestream local dev stop

Stop dev infrastructure.

```bash
typestream local dev stop
```

### typestream local dev clean

Stop dev infrastructure, remove volumes, and purge built images.

```bash
typestream local dev clean
```

### typestream local seed

Pull and run the seeder container to populate additional sample data (books, authors, ratings, users). This is optional -- demo data generators start automatically with `typestream local dev`.

```bash
typestream local seed
```

### typestream local show

Print the Docker Compose configuration.

```bash
typestream local show
```
