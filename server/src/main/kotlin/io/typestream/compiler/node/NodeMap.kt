package io.typestream.compiler.node

import io.typestream.compiler.types.DataStream
import io.typestream.compiler.types.Encoding
import io.typestream.compiler.types.InferenceContext
import io.typestream.compiler.types.InferenceResult
import io.typestream.grpc.job_service.Job
import kotlinx.serialization.Serializable

@Serializable
data class NodeMap(
    override val id: String,
    val mapperExpr: String = "",
    val mapper: (KeyValue) -> KeyValue
) : Node {
    override fun inferOutputSchema(
        input: DataStream?,
        inputEncoding: Encoding?,
        context: InferenceContext
    ): InferenceResult {
        // TODO: Extract field transformations from mapper lambda for accurate typing
        // For MVP, assume Map doesn't change schema (identity function)
        val stream = input ?: error("map $id missing input")
        return InferenceResult(stream, inputEncoding ?: Encoding.AVRO)
    }

    override fun toProto(): Job.PipelineNode = Job.PipelineNode.newBuilder()
        .setId(id)
        .setMap(Job.MapNode.newBuilder().setMapperExpr(mapperExpr))
        .build()

    override fun applyToShell(dataStreams: List<DataStream>, context: ExecutionContext): List<DataStream> =
        dataStreams.map { mapper(KeyValue(it, it)).value }

    companion object {
        fun fromProto(proto: Job.PipelineNode, context: FromProtoContext): NodeMap {
            val expr = proto.map.mapperExpr
            return if (expr.startsWith("select ")) {
                val fields = expr.removePrefix("select ").trim().split(" ").map { it.trimStart('.') }
                NodeMap(proto.id, expr) { kv -> KeyValue(kv.key, kv.value.select(fields)) }
            } else {
                NodeMap(proto.id, expr) { kv -> kv }
            }
        }
    }
}
