package io.typestream.openai

import io.typestream.compiler.node.Node
import io.typestream.compiler.types.DataStream
import io.typestream.compiler.types.TypeRules
import io.typestream.grpc.job_service.Job

/**
 * Handler for OpenAiTransformer pipeline nodes.
 * Centralizes proto-to-node conversion and type inference for OpenAI transformations.
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

    /**
     * Infer the output schema for an OpenAiTransformer node.
     * Adds a String field (AI response) to the input schema.
     *
     * @param input The input DataStream schema
     * @param outputField Name of the field to add (default: "ai_response")
     * @return DataStream with the new field added
     * @throws IllegalArgumentException if input schema is not a struct
     */
    fun inferType(input: DataStream, outputField: String): DataStream {
        return TypeRules.inferOpenAiTransformer(input, outputField)
    }
}
