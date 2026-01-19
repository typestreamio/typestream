package io.typestream.textextractor

import io.typestream.compiler.node.Node
import io.typestream.grpc.job_service.Job

/**
 * Handler for TextExtractor pipeline nodes.
 * Centralizes proto-to-node conversion for text extraction.
 *
 * Note: Schema inference is now handled by Node.TextExtractor.inferOutputSchema().
 */
object TextExtractorNodeHandler {

    /**
     * Check if this handler can process the given proto node.
     */
    fun canHandle(proto: Job.PipelineNode): Boolean = proto.hasTextExtractor()

    /**
     * Convert TextExtractor proto to internal Node type.
     */
    fun fromProto(proto: Job.PipelineNode): Node.TextExtractor {
        require(proto.hasTextExtractor()) { "Expected TextExtractor node, got: ${proto.nodeTypeCase}" }
        val t = proto.textExtractor
        val outputField = if (t.outputField.isBlank()) "text" else t.outputField
        return Node.TextExtractor(proto.id, t.filePathField, outputField)
    }
}
