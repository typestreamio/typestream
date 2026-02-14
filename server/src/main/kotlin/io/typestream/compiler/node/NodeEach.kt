package io.typestream.compiler.node

import io.typestream.compiler.types.DataStream
import io.typestream.compiler.types.Encoding
import io.typestream.compiler.types.InferenceContext
import io.typestream.compiler.types.InferenceResult
import io.typestream.grpc.job_service.Job
import kotlinx.serialization.Serializable

@Serializable
data class NodeEach(
    override val id: String,
    val fnExpr: String = "",
    val fn: (KeyValue) -> Unit
) : Node {
    override fun inferOutputSchema(
        input: DataStream?,
        inputEncoding: Encoding?,
        context: InferenceContext
    ): InferenceResult {
        val stream = input ?: error("each $id missing input")
        return InferenceResult(stream, inputEncoding ?: Encoding.AVRO)
    }

    override fun toProto(): Job.PipelineNode = Job.PipelineNode.newBuilder()
        .setId(id)
        .setEach(Job.EachNode.newBuilder().setFnExpr(fnExpr))
        .build()

    override fun applyToShell(dataStreams: List<DataStream>, context: ExecutionContext): List<DataStream> {
        dataStreams.forEach { fn(KeyValue(it, it)) }
        return dataStreams
    }

    companion object {
        fun fromProto(proto: Job.PipelineNode, context: FromProtoContext): NodeEach =
            NodeEach(proto.id, proto.each.fnExpr) { _ -> }
    }
}
