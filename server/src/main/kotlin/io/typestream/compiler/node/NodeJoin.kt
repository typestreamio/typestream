package io.typestream.compiler.node

import io.typestream.compiler.types.DataStream
import io.typestream.compiler.types.Encoding
import io.typestream.compiler.types.InferenceContext
import io.typestream.compiler.types.InferenceResult
import io.typestream.grpc.job_service.Job
import kotlinx.serialization.Serializable

@Serializable
data class NodeJoin(override val id: String, val with: DataStream, val joinType: JoinType) : Node {
    override fun inferOutputSchema(
        input: DataStream?,
        inputEncoding: Encoding?,
        context: InferenceContext
    ): InferenceResult {
        val stream = input ?: error("join $id missing input")
        val merged = stream.merge(with).copy(originalAvroSchema = null)
        return InferenceResult(merged, inputEncoding ?: Encoding.AVRO)
    }

    override fun toProto(): Job.PipelineNode = Job.PipelineNode.newBuilder()
        .setId(id)
        .setJoin(
            Job.JoinNode.newBuilder()
                .setWith(Job.DataStreamProto.newBuilder().setPath(with.path))
                .setJoinType(
                    Job.JoinTypeProto.newBuilder()
                        .setByKey(joinType.byKey)
                        .setIsLookup(joinType.isLookup)
                )
        )
        .build()

    override fun applyToShell(dataStreams: List<DataStream>, context: ExecutionContext): List<DataStream> =
        throw UnsupportedOperationException("Join not supported in shell mode")

    companion object {
        fun fromProto(proto: Job.PipelineNode, context: FromProtoContext): NodeJoin {
            val j = proto.join
            val with = context.findDataStream(j.with.path)
            return NodeJoin(proto.id, with, JoinType(j.joinType.byKey, j.joinType.isLookup))
        }
    }
}
