# Schema Propagation

Schema propagation is TypeStream's compile-time pass that flows type information through the pipeline graph. It enables schema validation before execution, field-name display in the GUI, and type-safe pipeline configurations.

## How it works

1. **Source resolution**: Source nodes look up their schema from Schema Registry via the [virtual filesystem](virtual-filesystem.md). For example, a topic encoded in Avro has its full field structure resolved at compile time.

2. **Node-by-node inference**: Each downstream node's `inferOutputSchema()` receives the upstream schema and produces an output schema:
   - **Pass-through nodes** (Filter, Count, Inspector, Each, Group): Schema is unchanged
   - **Schema-modifying nodes**: Transform the schema in specific ways

3. **Validation**: If a node references a field that doesn't exist in the input schema (e.g. a GeoIP node with `ipField: "ip_address"` when no such field exists), the error is caught at compile time -- before any Kafka Streams resources are allocated.

## Schema-modifying nodes

| Node | Schema Change |
|------|--------------|
| **Join** | Merges left and right schemas into a combined struct |
| **GeoIp** | Adds `outputField` (string) |
| **TextExtractor** | Adds `outputField` (string) |
| **EmbeddingGenerator** | Adds `outputField` (list of floats) |
| **OpenAiTransformer** | Adds `outputField` (string) |

## Encoding rules

The output encoding follows these rules:

- If the output schema is the same type as the input (e.g. filtering doesn't change the schema), the encoding is preserved (Avro stays Avro).
- If the output schema differs from the input (e.g. a join combines two schemas), the encoding defaults to JSON.
- If the source encoding is not set, it defaults to Avro.

### Examples

```sh
# Input: Avro. Output: Avro (schema unchanged by filter)
cat books | grep "Station" > station_books

# Input: Avro. Output: JSON (schema changed by cut)
cat books | cut title > book_titles

# Input: Avro + JSON. Output: JSON (mixed encodings)
join books ratings > book_ratings
```

## Why this matters

- **Catch errors early**: A misconfigured `ipField` or missing topic fails at plan/validate time, not after deployment.
- **GUI feedback**: The graph builder shows field names on every edge as you build, so you can verify the pipeline is correct before submitting.
- **Documentation**: Schema information is available programmatically, making it easy to understand what data flows through each stage of the pipeline.
