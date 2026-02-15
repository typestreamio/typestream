# CDC and Debezium

Change Data Capture (CDC) is a technique for streaming database changes (inserts, updates, deletes) into Kafka topics in real time. TypeStream uses [Debezium](https://debezium.io/) to capture changes from PostgreSQL (and other databases) via Kafka Connect.

## How CDC works

1. **PostgreSQL WAL**: The database is configured with `wal_level = logical`, which makes PostgreSQL write detailed change events to its write-ahead log.

2. **Debezium connector**: A Kafka Connect source connector reads the WAL and produces a change event for every row-level operation (INSERT, UPDATE, DELETE).

3. **Kafka topics**: Each table gets its own topic. Events arrive in Debezium's envelope format.

4. **TypeStream processing**: Pipelines consume these topics like any other Kafka topic, with optional CDC envelope unwrapping.

## The CDC envelope

Each Debezium event wraps the change in an envelope with metadata:

```json
{
  "before": null,
  "after": {
    "id": 1,
    "user_id": 42,
    "amount": 99.99,
    "created_at": "2024-01-15T10:30:00Z"
  },
  "op": "c",
  "source": {
    "connector": "postgresql",
    "db": "demo",
    "schema": "public",
    "table": "orders"
  },
  "ts_ms": 1705312200000
}
```

| Field | Description |
|-------|-------------|
| `before` | Row state before the change (`null` for inserts) |
| `after` | Row state after the change (`null` for deletes) |
| `op` | Operation type: `c` (create), `u` (update), `d` (delete), `r` (snapshot read) |
| `source` | Metadata about the source database, schema, and table |
| `ts_ms` | Timestamp of the change event |

## Unwrapping the envelope

Most pipelines care about the current row state, not the full envelope. TypeStream's `unwrapCdc` option extracts just the `after` payload:

- **With envelope**: Schema is the full Debezium struct (before, after, op, source, ts_ms)
- **With `unwrapCdc: true`**: Schema is just the table columns (id, user_id, amount, created_at)

In the GUI, **Postgres Source** nodes enable CDC unwrapping automatically.

## Topic naming convention

Debezium uses the format `<server-name>.<schema>.<table>`:

| PostgreSQL Table | Kafka Topic |
|-----------------|-------------|
| `public.orders` | `demo.public.orders` |
| `public.users` | `demo.public.users` |
| `public.file_uploads` | `demo.public.file_uploads` |

The `demo` prefix is the Debezium connector's configured server name. These topics appear in TypeStream's virtual filesystem at `/dev/kafka/local/topics/demo.public.<table>`.

## Local setup

The TypeStream local development stack includes a pre-configured PostgreSQL instance with three demo tables (`users`, `orders`, `file_uploads`). The Debezium connector is auto-registered on startup -- no manual configuration needed.

See [Set Up Postgres CDC](../how-to/setup-postgres-cdc.md) for a step-by-step guide.
