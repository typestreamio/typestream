package io.typestream.embedding

import io.typestream.compiler.node.Node
import io.typestream.grpc.job_service.Job

/**
 * Handler for EmbeddingGenerator pipeline nodes.
 * Centralizes proto-to-node conversion for embedding generation.
 *
 * Note: Schema inference is now handled by Node.EmbeddingGenerator.inferOutputSchema().
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
}
