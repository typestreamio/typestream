package io.typestream.embedding

import io.typestream.compiler.node.Node
import io.typestream.compiler.types.DataStream
import io.typestream.compiler.types.TypeRules
import io.typestream.grpc.job_service.Job

/**
 * Handler for EmbeddingGenerator pipeline nodes.
 * Centralizes proto-to-node conversion and type inference for embedding generation.
 */
object EmbeddingGeneratorNodeHandler {

    private const val DEFAULT_OUTPUT_FIELD = "embedding"
    private const val DEFAULT_MODEL = "text-embedding-3-small"

    /**
     * Check if this handler can process the given proto node.
     */
    fun canHandle(proto: Job.PipelineNode): Boolean = proto.hasEmbeddingGenerator()

    /**
     * Convert EmbeddingGenerator proto to internal Node type.
     */
    fun fromProto(proto: Job.PipelineNode): Node.EmbeddingGenerator {
        require(proto.hasEmbeddingGenerator()) { "Expected EmbeddingGenerator node, got: ${proto.nodeTypeCase}" }
        val e = proto.embeddingGenerator
        val outputField = if (e.outputField.isBlank()) DEFAULT_OUTPUT_FIELD else e.outputField
        val model = if (e.model.isBlank()) DEFAULT_MODEL else e.model
        return Node.EmbeddingGenerator(proto.id, e.textField, outputField, model)
    }

    /**
     * Infer the output schema for an EmbeddingGenerator node.
     * Adds a List<Float> field (embedding vector) to the input schema.
     *
     * @param input The input DataStream schema
     * @param textField Name of the field containing text to embed
     * @param outputField Name of the field to add (default: "embedding")
     * @return DataStream with the new field added
     * @throws IllegalArgumentException if textField doesn't exist in the input schema
     */
    fun inferType(input: DataStream, textField: String, outputField: String): DataStream {
        return TypeRules.inferEmbeddingGenerator(input, textField, outputField)
    }
}
