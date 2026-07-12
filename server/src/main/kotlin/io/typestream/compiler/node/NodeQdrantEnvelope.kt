package io.typestream.compiler.node

import io.typestream.compiler.types.DataStream
import io.typestream.compiler.types.Encoding
import io.typestream.compiler.types.InferenceContext
import io.typestream.compiler.types.InferenceResult
import io.typestream.compiler.types.schema.Schema
import io.typestream.grpc.job_service.Job
import kotlinx.serialization.Serializable

/**
 * Reshapes records into the envelope required by the qdrant-kafka sink connector:
 * `{ collection_name, id, vector, payload: { ... } }`. The connector reads only
 * these top-level fields and silently ignores everything else, so all record
 * content must be nested under `payload`.
 */
@Serializable
data class NodeQdrantEnvelope(
    override val id: String,
    val collectionName: String,
    val idField: String,
    val vectorField: String,
    val payloadFields: List<String>,
) : Node {
    override fun inferOutputSchema(
        input: DataStream?,
        inputEncoding: Encoding?,
        context: InferenceContext
    ): InferenceResult {
        val stream = input ?: error("qdrantEnvelope $id missing input")
        val inputSchema = stream.schema

        require(inputSchema is Schema.Struct) {
            "QdrantEnvelope requires struct schema, got: ${inputSchema::class.simpleName}"
        }

        val availableFields = inputSchema.value.map { it.name }

        val idValue = unwrapOptional(inputSchema.value.find { it.name == idField }?.value)
            ?: error("QdrantEnvelope ID field '$idField' not found in schema. Available fields: $availableFields")
        require(idValue is Schema.Int || idValue is Schema.Long || idValue is Schema.UUID || idValue is Schema.String) {
            "QdrantEnvelope ID field '$idField' must be an integer, UUID, or string (Qdrant point IDs must be an unsigned integer or a UUID), got: ${idValue.printTypes()}"
        }

        val vectorValue = unwrapOptional(inputSchema.value.find { it.name == vectorField }?.value)
            ?: error("QdrantEnvelope vector field '$vectorField' not found in schema. Available fields: $availableFields")
        require(vectorValue is Schema.List && (vectorValue.valueType is Schema.Float || vectorValue.valueType is Schema.Double)) {
            "QdrantEnvelope vector field '$vectorField' must be a list of floats, got: ${vectorValue.printTypes()}"
        }

        val missingPayloadFields = payloadFields.filter { name -> availableFields.none { it == name } }
        require(missingPayloadFields.isEmpty()) {
            "QdrantEnvelope payload fields $missingPayloadFields not found in schema. Available fields: $availableFields"
        }

        val outputStream = stream.copy(schema = reshape(inputSchema), originalAvroSchema = null)
        // The downstream sink writes schemaless JSON (its plainJson flag) because
        // qdrant-kafka cannot deserialize Connect Structs — report JSON here so the
        // UI shows the topic's real encoding.
        return InferenceResult(outputStream, Encoding.JSON)
    }

    /**
     * Builds the connector envelope from an input struct. Schema instances carry
     * both types and values, so the same reshape serves schema inference and
     * per-record execution.
     */
    fun reshape(input: Schema.Struct): Schema.Struct {
        val idValue = input.value.find { it.name == idField }?.value ?: Schema.Long(0L)
        val vectorValue = input.value.find { it.name == vectorField }?.value
            ?: Schema.List(emptyList(), Schema.Float(0.0f))

        val payload = input.value.filter { field ->
            if (payloadFields.isEmpty()) {
                field.name != idField && field.name != vectorField
            } else {
                payloadFields.contains(field.name)
            }
        }

        return Schema.Struct(
            listOf(
                Schema.Field("collection_name", Schema.String(collectionName)),
                Schema.Field("id", unwrapOptional(idValue) ?: idValue),
                Schema.Field("vector", unwrapOptional(vectorValue) ?: vectorValue),
                Schema.Field("payload", Schema.Struct(payload)),
            )
        )
    }

    fun reshape(dataStream: DataStream): DataStream {
        val schema = dataStream.schema
        require(schema is Schema.Struct) { "QdrantEnvelope requires struct schema, got: ${schema::class.simpleName}" }
        return dataStream.copy(schema = reshape(schema), originalAvroSchema = null)
    }

    override fun toProto(): Job.PipelineNode = Job.PipelineNode.newBuilder()
        .setId(id)
        .setQdrantEnvelope(
            Job.QdrantEnvelopeNode.newBuilder()
                .setCollectionName(collectionName)
                .setIdField(idField)
                .setVectorField(vectorField)
                .setPayloadFields(payloadFields.joinToString(","))
        )
        .build()

    override fun applyToShell(dataStreams: List<DataStream>, context: ExecutionContext): List<DataStream> =
        dataStreams.map { reshape(it) }

    companion object {
        private const val DEFAULT_ID_FIELD = "id"
        private const val DEFAULT_VECTOR_FIELD = "embedding"

        fun parsePayloadFields(payloadFields: String): List<String> =
            payloadFields.split(",").map { it.trim() }.filter { it.isNotBlank() }

        fun fromProto(proto: Job.PipelineNode, context: FromProtoContext): NodeQdrantEnvelope {
            val q = proto.qdrantEnvelope
            return NodeQdrantEnvelope(
                proto.id,
                q.collectionName,
                q.idField.ifBlank { DEFAULT_ID_FIELD },
                q.vectorField.ifBlank { DEFAULT_VECTOR_FIELD },
                parsePayloadFields(q.payloadFields),
            )
        }

        private fun unwrapOptional(schema: Schema?): Schema? =
            if (schema is Schema.Optional) schema.value else schema
    }
}
