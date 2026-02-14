package io.typestream.compiler.node

import io.typestream.compiler.toProtoEncoding
import io.typestream.compiler.types.DataStream
import io.typestream.compiler.types.Encoding
import io.typestream.compiler.types.InferenceContext
import io.typestream.compiler.types.InferenceResult
import io.typestream.grpc.job_service.Job
import kotlinx.serialization.Serializable

@Serializable
data class NodeSink(override val id: String, val output: DataStream, val encoding: Encoding) : Node {
    override fun inferOutputSchema(
        input: DataStream?,
        inputEncoding: Encoding?,
        context: InferenceContext
    ): InferenceResult {
        val stream = input ?: error("sink $id missing input")
        val outputStream = stream.copy(path = output.path)
        return InferenceResult(outputStream, inputEncoding ?: Encoding.AVRO)
    }

    override fun toProto(): Job.PipelineNode = Job.PipelineNode.newBuilder()
        .setId(id)
        .setSink(
            Job.SinkNode.newBuilder()
                .setOutput(Job.DataStreamProto.newBuilder().setPath(output.path))
                .setEncoding(encoding.toProtoEncoding())
        )
        .build()

    override fun applyToShell(dataStreams: List<DataStream>, context: ExecutionContext): List<DataStream> =
        throw UnsupportedOperationException("Sink not supported in shell mode")

    companion object {
        fun fromProto(proto: Job.PipelineNode, context: FromProtoContext): NodeSink {
            val out = context.inferredSchemas[proto.id]
                ?: error("No inferred schema for sink ${proto.id}")
            val encoding = context.inferredEncodings[proto.id]
                ?: error("No inferred encoding for sink ${proto.id}")
            return NodeSink(proto.id, out, encoding)
        }
    }
}
