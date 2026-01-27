package io.typestream.compiler.node

import io.typestream.compiler.ast.Predicate
import io.typestream.compiler.types.DataStream
import io.typestream.compiler.types.Encoding
import io.typestream.compiler.types.InferenceContext
import io.typestream.compiler.types.InferenceResult
import io.typestream.compiler.types.schema.Schema
import kotlinx.serialization.Serializable

@Serializable
sealed interface Node {
    val id: String

    /**
     * Infer the output schema and encoding for this node.
     * Each node type encapsulates its own schema transformation logic.
     *
     * @param input The input DataStream from the upstream node (null for source nodes)
     * @param inputEncoding The encoding from the upstream node (null for source nodes)
     * @param context Context for looking up external resources (catalog, etc.)
     * @return InferenceResult with output DataStream and encoding
     */
    fun inferOutputSchema(
        input: DataStream?,
        inputEncoding: Encoding?,
        context: InferenceContext
    ): InferenceResult

    @Serializable
    data class Count(override val id: String) : Node {
        override fun inferOutputSchema(
            input: DataStream?,
            inputEncoding: Encoding?,
            context: InferenceContext
        ): InferenceResult {
            val stream = input ?: error("count $id missing input")
            return InferenceResult(stream, inputEncoding ?: Encoding.AVRO)
        }
    }

    @Serializable
    data class WindowedCount(override val id: String, val windowSizeSeconds: Long) : Node {
        override fun inferOutputSchema(
            input: DataStream?,
            inputEncoding: Encoding?,
            context: InferenceContext
        ): InferenceResult {
            val stream = input ?: error("windowedCount $id missing input")
            return InferenceResult(stream, inputEncoding ?: Encoding.AVRO)
        }
    }

    @Serializable
    data class Filter(override val id: String, val byKey: Boolean, val predicate: Predicate) : Node {
        override fun inferOutputSchema(
            input: DataStream?,
            inputEncoding: Encoding?,
            context: InferenceContext
        ): InferenceResult {
            val stream = input ?: error("filter $id missing input")
            return InferenceResult(stream, inputEncoding ?: Encoding.AVRO)
        }
    }

    @Serializable
    data class Group(override val id: String, val keyMapper: (KeyValue) -> DataStream) : Node {
        override fun inferOutputSchema(
            input: DataStream?,
            inputEncoding: Encoding?,
            context: InferenceContext
        ): InferenceResult {
            val stream = input ?: error("group $id missing input")
            return InferenceResult(stream, inputEncoding ?: Encoding.AVRO)
        }
    }

    @Serializable
    data class Join(override val id: String, val with: DataStream, val joinType: JoinType) : Node {
        override fun inferOutputSchema(
            input: DataStream?,
            inputEncoding: Encoding?,
            context: InferenceContext
        ): InferenceResult {
            val stream = input ?: error("join $id missing input")
            // Merge left and right schemas, clear originalAvroSchema since join creates a new combined schema
            val merged = stream.merge(with).copy(originalAvroSchema = null)
            return InferenceResult(merged, inputEncoding ?: Encoding.AVRO)
        }
    }

    @Serializable
    data class Map(override val id: String, val mapper: (KeyValue) -> KeyValue) : Node {
        override fun inferOutputSchema(
            input: DataStream?,
            inputEncoding: Encoding?,
            context: InferenceContext
        ): InferenceResult {
            // TODO: Extract field transformations from mapper lambda for accurate typing
            // For MVP, assume Map doesn't change schema (identity function)
            val stream = input ?: error("map $id missing input")
            return InferenceResult(stream, inputEncoding ?: Encoding.AVRO)
        }
    }

    @Serializable
    data class NoOp(override val id: String) : Node {
        override fun inferOutputSchema(
            input: DataStream?,
            inputEncoding: Encoding?,
            context: InferenceContext
        ): InferenceResult {
            // NoOp can be used as root node with no input, or as a pass-through
            val stream = input ?: DataStream("", Schema.String.zeroValue)
            return InferenceResult(stream, inputEncoding ?: Encoding.AVRO)
        }
    }

    @Serializable
    data class ShellSource(override val id: String, val data: List<DataStream>) : Node {
        override fun inferOutputSchema(
            input: DataStream?,
            inputEncoding: Encoding?,
            context: InferenceContext
        ): InferenceResult {
            val stream = data.firstOrNull()
                ?: error("ShellSource $id has no data streams")
            // Shell sources always use JSON encoding
            return InferenceResult(stream, Encoding.JSON)
        }
    }

    @Serializable
    data class StreamSource(
        override val id: String,
        val dataStream: DataStream,
        val encoding: Encoding,
        val unwrapCdc: Boolean = false
    ) : Node {
        override fun inferOutputSchema(
            input: DataStream?,
            inputEncoding: Encoding?,
            context: InferenceContext
        ): InferenceResult {
            // StreamSource has pre-resolved dataStream and encoding
            // If unwrapCdc is true, extract 'after' payload from CDC envelope
            val outputStream = if (unwrapCdc) dataStream.unwrapCdc() else dataStream
            return InferenceResult(outputStream, encoding)
        }
    }

    @Serializable
    data class Each(override val id: String, val fn: (KeyValue) -> Unit) : Node {
        override fun inferOutputSchema(
            input: DataStream?,
            inputEncoding: Encoding?,
            context: InferenceContext
        ): InferenceResult {
            val stream = input ?: error("each $id missing input")
            return InferenceResult(stream, inputEncoding ?: Encoding.AVRO)
        }
    }

    @Serializable
    data class Sink(override val id: String, val output: DataStream, val encoding: Encoding) : Node {
        override fun inferOutputSchema(
            input: DataStream?,
            inputEncoding: Encoding?,
            context: InferenceContext
        ): InferenceResult {
            val stream = input ?: error("sink $id missing input")
            // Sink copies input schema with the target path
            val outputStream = stream.copy(path = output.path)
            return InferenceResult(outputStream, inputEncoding ?: Encoding.AVRO)
        }
    }

    @Serializable
    data class GeoIp(override val id: String, val ipField: String, val outputField: String) : Node {
        override fun inferOutputSchema(
            input: DataStream?,
            inputEncoding: Encoding?,
            context: InferenceContext
        ): InferenceResult {
            val stream = input ?: error("geoIp $id missing input")
            val inputSchema = stream.schema

            require(inputSchema is Schema.Struct) {
                "GeoIp requires struct schema, got: ${inputSchema::class.simpleName}"
            }

            val hasIpField = inputSchema.value.any { it.name == ipField }
            require(hasIpField) {
                "GeoIp IP field '$ipField' not found in schema. Available fields: ${inputSchema.value.map { it.name }}"
            }

            val newField = Schema.Field(outputField, Schema.String.zeroValue)
            val newFields = inputSchema.value + newField
            // Clear originalAvroSchema since we're modifying the schema
            val outputStream = stream.copy(schema = Schema.Struct(newFields), originalAvroSchema = null)
            return InferenceResult(outputStream, inputEncoding ?: Encoding.AVRO)
        }
    }

    @Serializable
    data class Inspector(override val id: String, val label: String = "") : Node {
        override fun inferOutputSchema(
            input: DataStream?,
            inputEncoding: Encoding?,
            context: InferenceContext
        ): InferenceResult {
            val stream = input ?: error("inspector $id missing input")
            return InferenceResult(stream, inputEncoding ?: Encoding.AVRO)
        }
    }

    @Serializable
    data class ReduceLatest(override val id: String) : Node {
        override fun inferOutputSchema(
            input: DataStream?,
            inputEncoding: Encoding?,
            context: InferenceContext
        ): InferenceResult {
            val stream = input ?: error("reduceLatest $id missing input")
            return InferenceResult(stream, inputEncoding ?: Encoding.AVRO)
        }
    }

    @Serializable
    data class TextExtractor(override val id: String, val filePathField: String, val outputField: String) : Node {
        override fun inferOutputSchema(
            input: DataStream?,
            inputEncoding: Encoding?,
            context: InferenceContext
        ): InferenceResult {
            val stream = input ?: error("textExtractor $id missing input")
            val inputSchema = stream.schema

            require(inputSchema is Schema.Struct) {
                "TextExtractor requires struct schema, got: ${inputSchema::class.simpleName}"
            }

            val hasFilePathField = inputSchema.value.any { it.name == filePathField }
            require(hasFilePathField) {
                "TextExtractor file path field '$filePathField' not found in schema. Available fields: ${inputSchema.value.map { it.name }}"
            }

            val newField = Schema.Field(outputField, Schema.String.zeroValue)
            val newFields = inputSchema.value + newField
            // Clear originalAvroSchema since we're modifying the schema
            val outputStream = stream.copy(schema = Schema.Struct(newFields), originalAvroSchema = null)
            return InferenceResult(outputStream, inputEncoding ?: Encoding.AVRO)
        }
    }

    @Serializable
    data class EmbeddingGenerator(
        override val id: String,
        val textField: String,
        val outputField: String,
        val model: String
    ) : Node {
        override fun inferOutputSchema(
            input: DataStream?,
            inputEncoding: Encoding?,
            context: InferenceContext
        ): InferenceResult {
            val stream = input ?: error("embeddingGenerator $id missing input")
            val inputSchema = stream.schema

            require(inputSchema is Schema.Struct) {
                "EmbeddingGenerator requires struct schema, got: ${inputSchema::class.simpleName}"
            }

            val hasTextField = inputSchema.value.any { it.name == textField }
            require(hasTextField) {
                "EmbeddingGenerator text field '$textField' not found in schema. Available fields: ${inputSchema.value.map { it.name }}"
            }

            // Embedding is a List<Float>
            val embeddingType = Schema.List(emptyList(), Schema.Float(0.0f))
            val newField = Schema.Field(outputField, embeddingType)
            val newFields = inputSchema.value + newField
            // Clear originalAvroSchema since we're modifying the schema
            val outputStream = stream.copy(schema = Schema.Struct(newFields), originalAvroSchema = null)
            return InferenceResult(outputStream, inputEncoding ?: Encoding.AVRO)
        }
    }

    @Serializable
    data class OpenAiTransformer(
        override val id: String,
        val prompt: String,
        val outputField: String,
        val model: String
    ) : Node {
        override fun inferOutputSchema(
            input: DataStream?,
            inputEncoding: Encoding?,
            context: InferenceContext
        ): InferenceResult {
            val stream = input ?: error("openAiTransformer $id missing input")
            val inputSchema = stream.schema

            require(inputSchema is Schema.Struct) {
                "OpenAiTransformer requires struct schema, got: ${inputSchema::class.simpleName}"
            }

            val newField = Schema.Field(outputField, Schema.String.zeroValue)
            val newFields = inputSchema.value + newField
            // Clear originalAvroSchema since we're modifying the schema
            val outputStream = stream.copy(schema = Schema.Struct(newFields), originalAvroSchema = null)
            return InferenceResult(outputStream, inputEncoding ?: Encoding.AVRO)
        }
    }
}
