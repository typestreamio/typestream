package io.typestream.compiler.node

import io.typestream.compiler.types.DataStream
import io.typestream.compiler.types.Encoding
import io.typestream.compiler.types.InferenceContext
import io.typestream.compiler.types.InferenceResult
import io.typestream.compiler.types.schema.Schema
import io.typestream.grpc.job_service.Job
import io.typestream.textextractor.TextExtractorExecution
import kotlinx.serialization.Serializable

@Serializable
data class NodeTextExtractor(override val id: String, val filePathField: String, val outputField: String) : Node {
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
        val outputStream = stream.copy(schema = Schema.Struct(newFields), originalAvroSchema = null)
        return InferenceResult(outputStream, inputEncoding ?: Encoding.AVRO)
    }

    override fun toProto(): Job.PipelineNode = Job.PipelineNode.newBuilder()
        .setId(id)
        .setTextExtractor(
            Job.TextExtractorNode.newBuilder()
                .setFilePathField(filePathField)
                .setOutputField(outputField)
        )
        .build()

    override fun applyToShell(dataStreams: List<DataStream>, context: ExecutionContext): List<DataStream> {
        val service = context.textExtractorService ?: error("TextExtractorService not available")
        return TextExtractorExecution.applyToShell(this, dataStreams, service)
    }

    companion object {
        private const val DEFAULT_OUTPUT_FIELD = "text"

        fun fromProto(proto: Job.PipelineNode, context: FromProtoContext): NodeTextExtractor {
            val t = proto.textExtractor
            val outputField = if (t.outputField.isBlank()) DEFAULT_OUTPUT_FIELD else t.outputField
            return NodeTextExtractor(proto.id, t.filePathField, outputField)
        }
    }
}
