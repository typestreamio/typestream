package io.typestream.compiler.node

import io.typestream.compiler.types.DataStream
import io.typestream.compiler.types.Encoding
import io.typestream.compiler.types.InferenceContext
import io.typestream.compiler.types.InferenceResult
import io.typestream.grpc.job_service.Job
import kotlinx.serialization.Serializable

@Serializable
sealed interface Node {
    val id: String

    fun inferOutputSchema(
        input: DataStream?,
        inputEncoding: Encoding?,
        context: InferenceContext
    ): InferenceResult

    fun toProto(): Job.PipelineNode

    fun applyToShell(dataStreams: List<DataStream>, context: ExecutionContext): List<DataStream>

    companion object {
        fun fromProto(proto: Job.PipelineNode, context: FromProtoContext): Node = when {
            proto.hasCount() -> NodeCount.fromProto(proto, context)
            proto.hasWindowedCount() -> NodeWindowedCount.fromProto(proto, context)
            proto.hasFilter() -> NodeFilter.fromProto(proto, context)
            proto.hasGroup() -> NodeGroup.fromProto(proto, context)
            proto.hasJoin() -> NodeJoin.fromProto(proto, context)
            proto.hasMap() -> NodeMap.fromProto(proto, context)
            proto.hasNoop() -> NodeNoOp.fromProto(proto, context)
            proto.hasShellSource() -> NodeShellSource.fromProto(proto, context)
            proto.hasStreamSource() -> NodeStreamSource.fromProto(proto, context)
            proto.hasEach() -> NodeEach.fromProto(proto, context)
            proto.hasSink() -> NodeSink.fromProto(proto, context)
            proto.hasGeoIp() -> NodeGeoIp.fromProto(proto, context)
            proto.hasInspector() -> NodeInspector.fromProto(proto, context)
            proto.hasReduceLatest() -> NodeReduceLatest.fromProto(proto, context)
            proto.hasTextExtractor() -> NodeTextExtractor.fromProto(proto, context)
            proto.hasEmbeddingGenerator() -> NodeEmbeddingGenerator.fromProto(proto, context)
            proto.hasOpenAiTransformer() -> NodeOpenAiTransformer.fromProto(proto, context)
            else -> error("Unknown node type: $proto")
        }
    }
}
