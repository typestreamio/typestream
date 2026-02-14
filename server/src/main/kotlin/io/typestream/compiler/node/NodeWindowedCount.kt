package io.typestream.compiler.node

import io.typestream.compiler.types.DataStream
import io.typestream.compiler.types.Encoding
import io.typestream.compiler.types.InferenceContext
import io.typestream.compiler.types.InferenceResult
import io.typestream.grpc.job_service.Job
import kotlinx.serialization.Serializable

@Serializable
data class NodeWindowedCount(override val id: String, val windowSizeSeconds: Long) : Node {
    override fun inferOutputSchema(
        input: DataStream?,
        inputEncoding: Encoding?,
        context: InferenceContext
    ): InferenceResult {
        val stream = input ?: error("windowedCount $id missing input")
        return InferenceResult(stream, inputEncoding ?: Encoding.AVRO)
    }

    override fun toProto(): Job.PipelineNode = Job.PipelineNode.newBuilder()
        .setId(id)
        .setWindowedCount(Job.WindowedCountNode.newBuilder().setWindowSizeSeconds(windowSizeSeconds))
        .build()

    override fun applyToShell(dataStreams: List<DataStream>, context: ExecutionContext): List<DataStream> =
        throw UnsupportedOperationException("WindowedCount not supported in shell mode")

    companion object {
        fun fromProto(proto: Job.PipelineNode, context: FromProtoContext): NodeWindowedCount =
            NodeWindowedCount(proto.id, proto.windowedCount.windowSizeSeconds)
    }
}
