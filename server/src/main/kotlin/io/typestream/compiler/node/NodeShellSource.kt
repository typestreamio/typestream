package io.typestream.compiler.node

import io.typestream.compiler.types.DataStream
import io.typestream.compiler.types.Encoding
import io.typestream.compiler.types.InferenceContext
import io.typestream.compiler.types.InferenceResult
import io.typestream.grpc.job_service.Job
import kotlinx.serialization.Serializable

@Serializable
data class NodeShellSource(override val id: String, val data: List<DataStream>) : Node {
    override fun inferOutputSchema(
        input: DataStream?,
        inputEncoding: Encoding?,
        context: InferenceContext
    ): InferenceResult {
        val stream = data.firstOrNull()
            ?: error("ShellSource $id has no data streams")
        return InferenceResult(stream, Encoding.JSON)
    }

    override fun toProto(): Job.PipelineNode = Job.PipelineNode.newBuilder()
        .setId(id)
        .setShellSource(
            Job.ShellSourceNode.newBuilder()
                .addAllData(data.map { Job.DataStreamProto.newBuilder().setPath(it.path).build() })
        )
        .build()

    override fun applyToShell(dataStreams: List<DataStream>, context: ExecutionContext): List<DataStream> =
        dataStreams

    companion object {
        fun fromProto(proto: Job.PipelineNode, context: FromProtoContext): NodeShellSource {
            val data = proto.shellSource.dataList.map { dsProto -> context.findDataStream(dsProto.path) }
            return NodeShellSource(proto.id, data)
        }
    }
}
