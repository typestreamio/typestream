package io.typestream.compiler.node

import io.typestream.compiler.types.DataStream
import io.typestream.compiler.types.Encoding
import io.typestream.compiler.types.InferenceContext
import io.typestream.compiler.types.InferenceResult
import io.typestream.compiler.types.schema.Schema
import io.typestream.grpc.job_service.Job
import kotlinx.serialization.Serializable

/**
 * Reshapes a flat record into a vector-store envelope:
 *
 *     { <idOut>: <idField>, <vectorOut>: <vectorField>, <payloadOut>: { ...remaining fields } }
 *
 * Synthesized by [io.typestream.compiler.PipelineDesugarer] in front of sinks whose
 * connector consumes a fixed `{id, vector, payload}` JSON shape (e.g. Qdrant's
 * `io.qdrant.kafka.QdrantSinkConnector`). Output encoding is forced to JSON so the
 * downstream sink emits the literal envelope the connector expects.
 */
@Serializable
data class NodeVectorEnvelope(
    override val id: String,
    val idField: String,
    val vectorField: String,
    val idOut: String,
    val vectorOut: String,
    val payloadOut: String,
) : Node {
    /** Reshape a single record's [Schema.Struct] into the `{id, vector, payload}` envelope. */
    fun reshape(input: DataStream): DataStream {
        val schema = input.schema
        require(schema is Schema.Struct) {
            "VectorEnvelope requires struct schema, got: ${schema::class.simpleName}"
        }

        val idValue = schema.value.find { it.name == idField }?.value
            ?: error("VectorEnvelope id field '$idField' not found. Available: ${schema.value.map { it.name }}")
        val vectorValue = schema.value.find { it.name == vectorField }?.value
            ?: error("VectorEnvelope vector field '$vectorField' not found. Available: ${schema.value.map { it.name }}")

        val payloadFields = schema.value.filter { it.name != idField && it.name != vectorField }

        val envelope = Schema.Struct(
            listOf(
                Schema.Field(idOut, idValue),
                Schema.Field(vectorOut, vectorValue),
                Schema.Field(payloadOut, Schema.Struct(payloadFields)),
            )
        )

        return input.copy(schema = envelope, originalAvroSchema = null)
    }

    override fun inferOutputSchema(
        input: DataStream?,
        inputEncoding: Encoding?,
        context: InferenceContext
    ): InferenceResult {
        val stream = input ?: error("vectorEnvelope $id missing input")
        // The reshaped record is emitted as plain JSON regardless of the input encoding.
        return InferenceResult(reshape(stream), Encoding.JSON)
    }

    override fun toProto(): Job.PipelineNode = Job.PipelineNode.newBuilder()
        .setId(id)
        .setVectorEnvelope(
            Job.VectorEnvelopeNode.newBuilder()
                .setIdField(idField)
                .setVectorField(vectorField)
                .setIdOut(idOut)
                .setVectorOut(vectorOut)
                .setPayloadOut(payloadOut)
        )
        .build()

    override fun applyToShell(dataStreams: List<DataStream>, context: ExecutionContext): List<DataStream> =
        dataStreams.map { reshape(it) }

    companion object {
        const val DEFAULT_ID_FIELD = "id"
        const val DEFAULT_VECTOR_FIELD = "embedding"
        const val DEFAULT_ID_OUT = "id"
        const val DEFAULT_VECTOR_OUT = "vector"
        const val DEFAULT_PAYLOAD_OUT = "payload"

        fun fromProto(proto: Job.PipelineNode, context: FromProtoContext): NodeVectorEnvelope {
            val v = proto.vectorEnvelope
            return NodeVectorEnvelope(
                id = proto.id,
                idField = v.idField.ifBlank { DEFAULT_ID_FIELD },
                vectorField = v.vectorField.ifBlank { DEFAULT_VECTOR_FIELD },
                idOut = v.idOut.ifBlank { DEFAULT_ID_OUT },
                vectorOut = v.vectorOut.ifBlank { DEFAULT_VECTOR_OUT },
                payloadOut = v.payloadOut.ifBlank { DEFAULT_PAYLOAD_OUT },
            )
        }
    }
}
