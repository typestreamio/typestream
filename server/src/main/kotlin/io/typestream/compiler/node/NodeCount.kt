package io.typestream.compiler.node

import io.typestream.compiler.types.DataStream
import io.typestream.compiler.types.Encoding
import io.typestream.compiler.types.InferenceContext
import io.typestream.compiler.types.InferenceResult
import io.typestream.grpc.job_service.Job
import kotlinx.serialization.Serializable

@Serializable
data class NodeCount(override val id: String) : Node {
    override fun inferOutputSchema(
        input: DataStream?,
        inputEncoding: Encoding?,
        context: InferenceContext
    ): InferenceResult {
        val stream = input ?: error("count $id missing input")
        return InferenceResult(stream, inputEncoding ?: Encoding.AVRO)
    }

    override fun toProto(): Job.PipelineNode = Job.PipelineNode.newBuilder()
        .setId(id)
        .setCount(Job.CountNode.newBuilder())
        .build()

    override fun applyToShell(dataStreams: List<DataStream>, context: ExecutionContext): List<DataStream> =
        throw UnsupportedOperationException("Count not supported in shell mode")

    companion object {
        fun fromProto(proto: Job.PipelineNode, context: FromProtoContext): NodeCount = NodeCount(proto.id)
    }
}
