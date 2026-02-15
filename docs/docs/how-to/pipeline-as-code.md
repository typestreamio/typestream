---
description: Define, validate, and manage pipelines as versioned JSON files.
---

# Pipeline-as-Code

This guide covers how to define TypeStream pipelines as `.typestream.json` files and manage them with the CLI -- similar to how you'd use Terraform or Kubernetes manifests.

## Prerequisites

- TypeStream [installed](../installation.mdx) and running
- Demo data generators running (started automatically with `typestream local dev`)

## File format

A pipeline file is a JSON document with four fields:

```json
{
  "name": "my-pipeline",
  "version": "1",
  "description": "What this pipeline does",
  "graph": {
    "nodes": [ ... ],
    "edges": [ ... ]
  }
}
```

| Field | Required | Description |
|-------|----------|-------------|
| `name` | yes | Unique identifier for the pipeline |
| `version` | yes | Version string (for tracking changes) |
| `description` | no | Human-readable description |
| `graph` | yes | A `UserPipelineGraph` containing nodes and edges |

The `graph` field defines the pipeline as a directed acyclic graph. Each node has an `id` and exactly one node type field (e.g. `kafkaSource`, `filter`, `kafkaSink`). Edges connect nodes by their IDs.

See the [Node Reference](../reference/node-reference.md) for all available node types and their configuration fields.

## Validate

Check a pipeline file for errors without applying it:

```bash
typestream validate my-pipeline.typestream.json
```

This verifies:
- JSON syntax is valid
- All referenced topics exist
- Node configurations are correct
- Schema propagation succeeds

## Plan

Preview what changes `apply` would make -- like `terraform plan`:

```bash
typestream plan my-pipeline.typestream.json
```

Output shows color-coded actions:

| Action | Meaning |
|--------|---------|
| **CREATE** (green) | New pipeline, will be created |
| **UPDATE** (yellow) | Pipeline exists, definition changed |
| **UNCHANGED** (dim) | Pipeline exists, no changes |
| **DELETE** (red) | Pipeline exists on server but not in files |

### Plan a directory

Scan a directory for all `*.typestream.json` files and show a combined diff:

```bash
typestream plan ./pipelines/
```

This is especially useful for CI/CD: you can see exactly what would change before applying.

## Apply

Create or update a pipeline:

```bash
typestream apply my-pipeline.typestream.json
```

The server compiles the graph, starts a Kafka Streams job, and persists the pipeline definition to a compacted Kafka topic (`__typestream_pipelines`). If the server restarts, all pipelines are automatically recovered.

## List and delete

List all managed pipelines:

```bash
typestream pipelines list
```

Delete a pipeline by name:

```bash
typestream pipelines delete my-pipeline
```

## Example: filter pipeline

```json
{
  "name": "webvisits-ok",
  "version": "1",
  "description": "Filter web visits to successful requests",
  "graph": {
    "nodes": [
      {
        "id": "source-1",
        "kafkaSource": {
          "topicPath": "/dev/kafka/local/topics/web_visits",
          "encoding": "AVRO"
        }
      },
      {
        "id": "filter-1",
        "filter": {
          "expression": ".status_code == 200"
        }
      },
      {
        "id": "sink-1",
        "kafkaSink": {
          "topicName": "web_visits_ok"
        }
      }
    ],
    "edges": [
      { "fromId": "source-1", "toId": "filter-1" },
      { "fromId": "filter-1", "toId": "sink-1" }
    ]
  }
}
```

## Example: aggregation pipeline

```json
{
  "name": "visits-by-status",
  "version": "1",
  "description": "Count web visits grouped by status code",
  "graph": {
    "nodes": [
      {
        "id": "source-1",
        "kafkaSource": {
          "topicPath": "/dev/kafka/local/topics/web_visits",
          "encoding": "AVRO"
        }
      },
      {
        "id": "mv-1",
        "materializedView": {
          "groupByField": "status_code",
          "aggregationType": "count"
        }
      }
    ],
    "edges": [
      { "fromId": "source-1", "toId": "mv-1" }
    ]
  }
}
```

## See also

- [Three Ways to Build](../concepts/three-ways.md) -- how config-as-code compares to CLI and GUI
- [Node Reference](../reference/node-reference.md) -- all node types and their fields
