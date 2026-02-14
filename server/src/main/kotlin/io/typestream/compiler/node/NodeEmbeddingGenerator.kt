package io.typestream.compiler.node

import io.typestream.compiler.types.DataStream
import io.typestream.compiler.types.Encoding
import io.typestream.compiler.types.InferenceContext
import io.typestream.compiler.types.InferenceResult
import io.typestream.compiler.types.schema.Schema
import io.typestream.embedding.EmbeddingGeneratorExecution
import io.typestream.grpc.job_service.Job
import kotlinx.serialization.Serializable

@Serializable
data class NodeEmbeddingGenerator(
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

        val embeddingType = Schema.List(emptyList(), Schema.Float(0.0f))
        val newField = Schema.Field(outputField, embeddingType)
        val newFields = inputSchema.value + newField
        val outputStream = stream.copy(schema = Schema.Struct(newFields), originalAvroSchema = null)
        return InferenceResult(outputStream, inputEncoding ?: Encoding.AVRO)
    }

    override fun toProto(): Job.PipelineNode = Job.PipelineNode.newBuilder()
        .setId(id)
        .setEmbeddingGenerator(
            Job.EmbeddingGeneratorNode.newBuilder()
                .setTextField(textField)
                .setOutputField(outputField)
                .setModel(model)
        )
        .build()

    override fun applyToShell(dataStreams: List<DataStream>, context: ExecutionContext): List<DataStream> {
        val service = context.embeddingGeneratorService ?: error("EmbeddingGeneratorService not available")
        return EmbeddingGeneratorExecution.applyToShell(this, dataStreams, service)
    }

    companion object {
        private const val DEFAULT_OUTPUT_FIELD = "embedding"
        private const val DEFAULT_MODEL = "text-embedding-3-small"

        fun fromProto(proto: Job.PipelineNode, context: FromProtoContext): NodeEmbeddingGenerator {
            val e = proto.embeddingGenerator
            val outputField = if (e.outputField.isBlank()) DEFAULT_OUTPUT_FIELD else e.outputField
            val model = if (e.model.isBlank()) DEFAULT_MODEL else e.model
            return NodeEmbeddingGenerator(proto.id, e.textField, outputField, model)
        }
    }
}
