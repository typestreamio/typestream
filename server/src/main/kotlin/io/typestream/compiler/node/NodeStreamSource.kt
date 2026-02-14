package io.typestream.compiler.node

import io.typestream.compiler.toProtoEncoding
import io.typestream.compiler.types.DataStream
import io.typestream.compiler.types.Encoding
import io.typestream.compiler.types.InferenceContext
import io.typestream.compiler.types.InferenceResult
import io.typestream.grpc.job_service.Job
import kotlinx.serialization.Serializable

@Serializable
data class NodeStreamSource(
    override val id: String,
    val dataStream: DataStream,
    val encoding: Encoding,
    val unwrapCdc: Boolean = false
) : Node {
    override fun inferOutputSchema(
        input: DataStream?,
        inputEncoding: Encoding?,
        context: InferenceContext
    ): InferenceResult {
        val outputStream = if (unwrapCdc) dataStream.unwrapCdc() else dataStream
        return InferenceResult(outputStream, encoding)
    }

    override fun toProto(): Job.PipelineNode = Job.PipelineNode.newBuilder()
        .setId(id)
        .setStreamSource(
            Job.StreamSourceNode.newBuilder()
                .setDataStream(Job.DataStreamProto.newBuilder().setPath(dataStream.path))
                .setEncoding(encoding.toProtoEncoding())
                .setUnwrapCdc(unwrapCdc)
        )
        .build()

    override fun applyToShell(dataStreams: List<DataStream>, context: ExecutionContext): List<DataStream> =
        throw UnsupportedOperationException("StreamSource not supported in shell mode")

    companion object {
        fun fromProto(proto: Job.PipelineNode, context: FromProtoContext): NodeStreamSource {
            val ss = proto.streamSource
            val ds = context.findDataStream(ss.dataStream.path)
            val encoding = context.inferredEncodings[proto.id]
                ?: error("No inferred encoding for stream source ${proto.id}")
            return NodeStreamSource(proto.id, ds, encoding, ss.unwrapCdc)
        }
    }
}
