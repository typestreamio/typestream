package io.typestream.compiler.node

import io.typestream.compiler.types.DataStream
import io.typestream.compiler.types.Encoding
import io.typestream.compiler.types.InferenceContext
import io.typestream.compiler.types.InferenceResult
import io.typestream.compiler.types.schema.Schema
import io.typestream.grpc.job_service.Job
import io.typestream.openai.OpenAiTransformerExecution
import kotlinx.serialization.Serializable

@Serializable
data class NodeOpenAiTransformer(
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
        val outputStream = stream.copy(schema = Schema.Struct(newFields), originalAvroSchema = null)
        return InferenceResult(outputStream, inputEncoding ?: Encoding.AVRO)
    }

    override fun toProto(): Job.PipelineNode = Job.PipelineNode.newBuilder()
        .setId(id)
        .setOpenAiTransformer(
            Job.OpenAiTransformerNode.newBuilder()
                .setPrompt(prompt)
                .setOutputField(outputField)
                .setModel(model)
        )
        .build()

    override fun applyToShell(dataStreams: List<DataStream>, context: ExecutionContext): List<DataStream> {
        val service = context.openAiService ?: error("OpenAiService not available")
        return OpenAiTransformerExecution.applyToShell(this, dataStreams, service)
    }

    companion object {
        private const val DEFAULT_OUTPUT_FIELD = "ai_response"
        private const val DEFAULT_MODEL = "gpt-4o-mini"

        fun fromProto(proto: Job.PipelineNode, context: FromProtoContext): NodeOpenAiTransformer {
            val t = proto.openAiTransformer
            val outputField = if (t.outputField.isBlank()) DEFAULT_OUTPUT_FIELD else t.outputField
            val model = if (t.model.isBlank()) DEFAULT_MODEL else t.model
            return NodeOpenAiTransformer(proto.id, t.prompt, outputField, model)
        }
    }
}
