package io.typestream.textextractor

import io.typestream.compiler.node.Node
import io.typestream.compiler.types.DataStream
import io.typestream.compiler.types.TypeRules
import io.typestream.grpc.job_service.Job

/**
 * Handler for TextExtractor pipeline nodes.
 * Centralizes proto-to-node conversion and type inference for text extraction.
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

    /**
     * Infer the output schema for a TextExtractor node.
     * Adds a string field (extracted text) to the input schema.
     *
     * @param input The input DataStream schema
     * @param filePathField Name of the field containing the file path
     * @param outputField Name of the field to add (default: "text")
     * @return DataStream with the new field added
     * @throws IllegalArgumentException if filePathField doesn't exist in the input schema
     */
    fun inferType(input: DataStream, filePathField: String, outputField: String): DataStream {
        return TypeRules.inferTextExtractor(input, filePathField, outputField)
    }
}
