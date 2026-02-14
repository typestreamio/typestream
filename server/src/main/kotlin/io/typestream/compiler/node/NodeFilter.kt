package io.typestream.compiler.node

import io.typestream.compiler.ast.Predicate
import io.typestream.compiler.ast.PredicateParser
import io.typestream.compiler.types.DataStream
import io.typestream.compiler.types.Encoding
import io.typestream.compiler.types.InferenceContext
import io.typestream.compiler.types.InferenceResult
import io.typestream.grpc.job_service.Job
import kotlinx.serialization.Serializable

@Serializable
data class NodeFilter(override val id: String, val byKey: Boolean, val predicate: Predicate) : Node {
    override fun inferOutputSchema(
        input: DataStream?,
        inputEncoding: Encoding?,
        context: InferenceContext
    ): InferenceResult {
        val stream = input ?: error("filter $id missing input")
        return InferenceResult(stream, inputEncoding ?: Encoding.AVRO)
    }

    override fun toProto(): Job.PipelineNode = Job.PipelineNode.newBuilder()
        .setId(id)
        .setFilter(
            Job.FilterNode.newBuilder()
                .setByKey(byKey)
                .setPredicate(Job.PredicateProto.newBuilder().setExpr(predicate.toExpr()))
        )
        .build()

    override fun applyToShell(dataStreams: List<DataStream>, context: ExecutionContext): List<DataStream> =
        dataStreams.filter { predicate.matches(it) }

    companion object {
        fun fromProto(proto: Job.PipelineNode, context: FromProtoContext): NodeFilter {
            val f = proto.filter
            return NodeFilter(proto.id, f.byKey, PredicateParser.parse(f.predicate.expr))
        }
    }
}
