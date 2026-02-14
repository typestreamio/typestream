package io.typestream.compiler.node

import io.typestream.compiler.types.DataStream
import io.typestream.compiler.types.Encoding
import io.typestream.compiler.types.InferenceContext
import io.typestream.compiler.types.InferenceResult
import io.typestream.grpc.job_service.Job
import kotlinx.serialization.Serializable

@Serializable
data class NodeGroup(
    override val id: String,
    val keyMapperExpr: String = "",
    val keyMapper: (KeyValue) -> DataStream
) : Node {
    override fun inferOutputSchema(
        input: DataStream?,
        inputEncoding: Encoding?,
        context: InferenceContext
    ): InferenceResult {
        val stream = input ?: error("group $id missing input")
        return InferenceResult(stream, inputEncoding ?: Encoding.AVRO)
    }

    override fun toProto(): Job.PipelineNode = Job.PipelineNode.newBuilder()
        .setId(id)
        .setGroup(Job.GroupNode.newBuilder().setKeyMapperExpr(keyMapperExpr))
        .build()

    override fun applyToShell(dataStreams: List<DataStream>, context: ExecutionContext): List<DataStream> =
        throw UnsupportedOperationException("Group not supported in shell mode")

    companion object {
        fun fromProto(proto: Job.PipelineNode, context: FromProtoContext): NodeGroup {
            val fieldPath = proto.group.keyMapperExpr
            val fields = fieldPath.trimStart('.').split('.').filter { it.isNotBlank() }
            return if (fields.isEmpty()) {
                NodeGroup(proto.id, fieldPath) { kv -> kv.key }
            } else {
                NodeGroup(proto.id, fieldPath) { kv -> kv.value.select(fields) }
            }
        }
    }
}
