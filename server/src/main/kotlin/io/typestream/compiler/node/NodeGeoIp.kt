package io.typestream.compiler.node

import io.typestream.compiler.types.DataStream
import io.typestream.compiler.types.Encoding
import io.typestream.compiler.types.InferenceContext
import io.typestream.compiler.types.InferenceResult
import io.typestream.compiler.types.schema.Schema
import io.typestream.geoip.GeoIpExecution
import io.typestream.grpc.job_service.Job
import kotlinx.serialization.Serializable

@Serializable
data class NodeGeoIp(override val id: String, val ipField: String, val outputField: String) : Node {
    override fun inferOutputSchema(
        input: DataStream?,
        inputEncoding: Encoding?,
        context: InferenceContext
    ): InferenceResult {
        val stream = input ?: error("geoIp $id missing input")
        val inputSchema = stream.schema

        require(inputSchema is Schema.Struct) {
            "GeoIp requires struct schema, got: ${inputSchema::class.simpleName}"
        }

        val hasIpField = inputSchema.value.any { it.name == ipField }
        require(hasIpField) {
            "GeoIp IP field '$ipField' not found in schema. Available fields: ${inputSchema.value.map { it.name }}"
        }

        val newField = Schema.Field(outputField, Schema.String.zeroValue)
        val newFields = inputSchema.value + newField
        val outputStream = stream.copy(schema = Schema.Struct(newFields), originalAvroSchema = null)
        return InferenceResult(outputStream, inputEncoding ?: Encoding.AVRO)
    }

    override fun toProto(): Job.PipelineNode = Job.PipelineNode.newBuilder()
        .setId(id)
        .setGeoIp(
            Job.GeoIpNode.newBuilder()
                .setIpField(ipField)
                .setOutputField(outputField)
        )
        .build()

    override fun applyToShell(dataStreams: List<DataStream>, context: ExecutionContext): List<DataStream> {
        val service = context.geoIpService ?: error("GeoIpService not available")
        return GeoIpExecution.applyToShell(this, dataStreams, service)
    }

    companion object {
        fun fromProto(proto: Job.PipelineNode, context: FromProtoContext): NodeGeoIp {
            val g = proto.geoIp
            return NodeGeoIp(proto.id, g.ipField, g.outputField)
        }
    }
}
