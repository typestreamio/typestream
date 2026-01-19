package io.typestream.openai

import io.typestream.compiler.node.Node
import io.typestream.grpc.job_service.Job

/**
 * Handler for OpenAiTransformer pipeline nodes.
 * Centralizes proto-to-node conversion for OpenAI transformations.
 *
 * Note: Schema inference is now handled by Node.OpenAiTransformer.inferOutputSchema().
 */
object OpenAiTransformerNodeHandler {

    private const val DEFAULT_OUTPUT_FIELD = "ai_response"
    private const val DEFAULT_MODEL = "gpt-4o-mini"

    /**
     * Check if this handler can process the given proto node.
     */
    fun canHandle(proto: Job.PipelineNode): Boolean = proto.hasOpenAiTransformer()

    /**
     * Convert OpenAiTransformer proto to internal Node type.
     */
    fun fromProto(proto: Job.PipelineNode): Node.OpenAiTransformer {
        require(proto.hasOpenAiTransformer()) { "Expected OpenAiTransformer node, got: ${proto.nodeTypeCase}" }
        val t = proto.openAiTransformer
        val outputField = if (t.outputField.isBlank()) DEFAULT_OUTPUT_FIELD else t.outputField
        val model = if (t.model.isBlank()) DEFAULT_MODEL else t.model
        return Node.OpenAiTransformer(proto.id, t.prompt, outputField, model)
    }
}
