# Composable Node Architecture

## Status

**Proposed** - GeoIP node implemented as reference pattern

## Context

The current architecture for adding pipeline nodes to the Graph UI path requires modifying 6+ central files:

1. `protos/job.proto` - Add message to PipelineNode oneof
2. `Node.kt` - Add data class to sealed interface
3. `GraphCompiler.kt` - Add case to `mapPipelineNode()` + `inferNodeType()`
4. `TypeRules.kt` - Add type inference function
5. `KafkaStreamsJob.kt` - Add case to `buildTopology()`
6. `Vm.kt` - Add case to shell execution
7. `KafkaStreamSource.kt` - Add execution method

This doesn't scale well. The text-based CLI compilation uses a cleaner `NodeResolver` pattern where each command knows how to resolve itself into a graph.

## Decision

Adopt a **handler + execution** pattern for proto-to-node compilation:

- **Nodes remain data classes** - Separation of data and behavior
- **Handlers** convert protos to nodes and infer types
- **Execution strategies** apply transformations on each runtime
- **CLI path unchanged** - Backwards compatible

## Architecture

### Directory Structure

```
server/src/main/kotlin/io/typestream/
├── nodes/                           # All node implementations
│   ├── registry/
│   │   ├── NodeHandlerRegistry.kt   # Handler lookup
│   │   └── NodeExecutionRegistry.kt # Execution lookup
│   ├── geoip/
│   │   ├── GeoIpService.kt          # Domain service
│   │   ├── GeoIpNodeHandler.kt      # Proto → Node + type inference
│   │   └── GeoIpExecution.kt        # Kafka + Shell execution
│   ├── filter/
│   │   ├── FilterNodeHandler.kt
│   │   └── FilterExecution.kt
│   └── ... (other nodes)
├── compiler/
│   ├── GraphCompiler.kt             # Uses NodeHandlerRegistry
│   └── node/
│       └── Node.kt                  # Sealed interface (data only)
└── scheduler/
    └── KafkaStreamsJob.kt           # Uses NodeExecutionRegistry
```

### Node Handler Interface

```kotlin
package io.typestream.nodes.registry

import io.typestream.compiler.node.Node
import io.typestream.compiler.types.DataStream
import io.typestream.compiler.types.Encoding
import io.typestream.filesystem.FileSystem
import io.typestream.grpc.job_service.Job

/**
 * Handler for converting proto PipelineNodes to internal Node types.
 * Each node type implements this to define its own conversion and type inference.
 */
interface PipelineNodeHandler {
    /**
     * Check if this handler can process the given proto node.
     */
    fun canHandle(proto: Job.PipelineNode): Boolean

    /**
     * Convert proto to internal Node type.
     */
    fun fromProto(
        proto: Job.PipelineNode,
        inferredSchemas: Map<String, DataStream>,
        inferredEncodings: Map<String, Encoding>
    ): Node

    /**
     * Infer the output schema and encoding for this node.
     * Called during Phase 1 of compilation before graph building.
     */
    fun inferType(
        proto: Job.PipelineNode,
        input: DataStream?,
        inputEncoding: Encoding?,
        fileSystem: FileSystem
    ): Pair<DataStream, Encoding>
}
```

### Node Execution Interface

```kotlin
package io.typestream.nodes.registry

import io.typestream.compiler.node.Node
import io.typestream.compiler.types.DataStream
import org.apache.kafka.streams.kstream.KStream

/**
 * Runtime services available during execution.
 */
data class RuntimeServices(
    val geoIpService: GeoIpService?,
    // Add other services as needed
)

/**
 * Execution strategy for a specific node type.
 * Implementations define how the node transforms data on each runtime.
 */
interface NodeExecution<T : Node> {
    /**
     * Check if this execution handles the given node.
     */
    fun canHandle(node: Node): Boolean

    /**
     * Apply transformation on Kafka Streams runtime.
     */
    fun applyToKafka(
        node: T,
        stream: KStream<DataStream, DataStream>,
        services: RuntimeServices
    ): KStream<DataStream, DataStream>

    /**
     * Apply transformation on Shell runtime.
     */
    fun applyToShell(
        node: T,
        dataStreams: List<DataStream>,
        services: RuntimeServices
    ): List<DataStream>
}
```

### Registry Implementation

```kotlin
package io.typestream.nodes.registry

object NodeHandlerRegistry {
    private val handlers: List<PipelineNodeHandler> = listOf(
        GeoIpNodeHandler,
        FilterNodeHandler,
        MapNodeHandler,
        // ... register all handlers
    )

    fun findHandler(proto: Job.PipelineNode): PipelineNodeHandler =
        handlers.firstOrNull { it.canHandle(proto) }
            ?: error("No handler for node type: ${proto.nodeTypeCase}")

    fun fromProto(
        proto: Job.PipelineNode,
        schemas: Map<String, DataStream>,
        encodings: Map<String, Encoding>
    ): Node = findHandler(proto).fromProto(proto, schemas, encodings)

    fun inferType(
        proto: Job.PipelineNode,
        input: DataStream?,
        inputEncoding: Encoding?,
        fs: FileSystem
    ): Pair<DataStream, Encoding> =
        findHandler(proto).inferType(proto, input, inputEncoding, fs)
}

object NodeExecutionRegistry {
    private val executions: List<NodeExecution<*>> = listOf(
        GeoIpExecution,
        FilterExecution,
        MapExecution,
        // ... register all executions
    )

    @Suppress("UNCHECKED_CAST")
    fun <T : Node> findExecution(node: T): NodeExecution<T> =
        executions.firstOrNull { it.canHandle(node) } as? NodeExecution<T>
            ?: error("No execution for node type: ${node::class.simpleName}")
}
```

### Example: GeoIP Implementation

```kotlin
// geoip/GeoIpNodeHandler.kt
package io.typestream.nodes.geoip

object GeoIpNodeHandler : PipelineNodeHandler {
    override fun canHandle(proto: Job.PipelineNode) = proto.hasGeoIp()

    override fun fromProto(
        proto: Job.PipelineNode,
        inferredSchemas: Map<String, DataStream>,
        inferredEncodings: Map<String, Encoding>
    ): Node.GeoIp {
        val g = proto.geoIp
        return Node.GeoIp(proto.id, g.ipField, g.outputField)
    }

    override fun inferType(
        proto: Job.PipelineNode,
        input: DataStream?,
        inputEncoding: Encoding?,
        fileSystem: FileSystem
    ): Pair<DataStream, Encoding> {
        requireNotNull(input) { "GeoIp node requires input" }
        val output = TypeRules.inferGeoIp(input, proto.geoIp.outputField)
        return output to (inputEncoding ?: Encoding.AVRO)
    }
}

// geoip/GeoIpExecution.kt
package io.typestream.nodes.geoip

object GeoIpExecution : NodeExecution<Node.GeoIp> {
    override fun canHandle(node: Node) = node is Node.GeoIp

    override fun applyToKafka(
        node: Node.GeoIp,
        stream: KStream<DataStream, DataStream>,
        services: RuntimeServices
    ): KStream<DataStream, DataStream> {
        val geoIp = services.geoIpService ?: error("GeoIpService not available")
        return stream.mapValues { value ->
            val ip = value.selectFieldAsString(node.ipField) ?: ""
            val country = geoIp.lookup(ip) ?: "UNKNOWN"
            value.addField(node.outputField, Schema.String(country))
        }
    }

    override fun applyToShell(
        node: Node.GeoIp,
        dataStreams: List<DataStream>,
        services: RuntimeServices
    ): List<DataStream> {
        val geoIp = services.geoIpService ?: error("GeoIpService not available")
        return dataStreams.map { ds ->
            val ip = ds.selectFieldAsString(node.ipField) ?: ""
            val country = geoIp.lookup(ip) ?: "UNKNOWN"
            ds.addField(node.outputField, Schema.String(country))
        }
    }
}
```

### Updated GraphCompiler (After Migration)

```kotlin
// Before: 100+ line when statement
private fun mapPipelineNode(...): Node = when {
    proto.hasGeoIp() -> { /* inline logic */ }
    proto.hasFilter() -> { /* inline logic */ }
    // ... 10+ more cases
}

// After: Single delegation
private fun mapPipelineNode(
    proto: Job.PipelineNode,
    inferredSchemas: Map<String, DataStream>,
    inferredEncodings: Map<String, Encoding>
): Node = NodeHandlerRegistry.fromProto(proto, inferredSchemas, inferredEncodings)
```

### Updated KafkaStreamsJob (After Migration)

```kotlin
// Before: when statement in buildTopology
sourceNode.walk { currentNode ->
    when (currentNode.ref) {
        is Node.GeoIp -> kafkaStreamSource.geoIp(currentNode.ref)
        is Node.Filter -> kafkaStreamSource.filter(currentNode.ref)
        // ... 10+ more cases
    }
}

// After: Registry lookup
sourceNode.walk { currentNode ->
    val execution = NodeExecutionRegistry.findExecution(currentNode.ref)
    stream = execution.applyToKafka(currentNode.ref, stream, runtimeServices)
}
```

## Migration Path

### Phase 1: Reference Implementation (Current)

1. Create `geoip/GeoIpNodeHandler.kt` and `geoip/GeoIpExecution.kt`
2. Update central files to delegate to these modules
3. Switch statements remain but are thin wrappers

### Phase 2: Create Handlers for All Node Types

Create handler + execution pairs for each existing node:
- FilterNodeHandler / FilterExecution
- MapNodeHandler / MapExecution
- JoinNodeHandler / JoinExecution
- etc.

### Phase 3: Introduce Registries

1. Create `NodeHandlerRegistry` and `NodeExecutionRegistry`
2. Register all handlers and executions
3. Update `GraphCompiler` to use `NodeHandlerRegistry`
4. Update `KafkaStreamsJob` and `Vm` to use `NodeExecutionRegistry`

### Phase 4: Remove Switch Statements

After all nodes use registries, remove the central switch statements entirely.

## Adding a New Node Type (After Migration)

To add a new node (e.g., `Encrypt`):

1. **Define proto** in `job.proto`:
   ```protobuf
   message EncryptNode {
     string field = 1;
     string algorithm = 2;
   }
   // Add to PipelineNode oneof
   ```

2. **Define data class** in `Node.kt`:
   ```kotlin
   data class Encrypt(override val id: String, val field: String, val algorithm: String) : Node
   ```

3. **Create handler and execution** in `nodes/encrypt/`:
   ```kotlin
   // EncryptNodeHandler.kt
   object EncryptNodeHandler : PipelineNodeHandler { ... }

   // EncryptExecution.kt
   object EncryptExecution : NodeExecution<Node.Encrypt> { ... }
   ```

4. **Register** in registries (or use auto-discovery)

**No changes to `GraphCompiler`, `KafkaStreamsJob`, `Vm`, or any other central files.**

## Consequences

### Benefits

- **Single Responsibility**: Each node type is self-contained in its own package
- **Open/Closed**: Add new nodes without modifying existing code
- **Testability**: Test handlers and executions in isolation
- **Discoverability**: All node logic in one place (`nodes/<type>/`)

### Trade-offs

- **Registry overhead**: Small runtime cost for handler lookup (negligible)
- **More files**: Each node type has 2-3 files instead of inline code
- **Discovery mechanism**: Need to register handlers (could use reflection/annotation processing)

### Mitigations

- Use compile-time registration (explicit list) for type safety
- Consider code generation from proto for boilerplate
- Keep `TypeRules.kt` as single source of truth for type inference formulas

## References

- Text compiler's `NodeResolver` pattern in `server/src/main/kotlin/io/typestream/compiler/ast/`
- Existing `GeoIpService` as example of domain service extraction
