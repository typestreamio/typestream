package io.typestream.compiler

import io.typestream.compiler.RuntimeType.KAFKA
import io.typestream.compiler.RuntimeType.SHELL
import io.typestream.compiler.node.Node
import io.typestream.compiler.node.NodeShellSource
import io.typestream.compiler.node.NodeSink
import io.typestream.compiler.node.NodeStreamSource
import io.typestream.compiler.types.DataStream
import io.typestream.filesystem.FileSystem
import io.typestream.graph.Graph
import io.typestream.grpc.job_service.Job as ProtoJob

data class Program(
    val id: String,
    val graph: Graph<Node>,
    val pipelineGraph: ProtoJob.PipelineGraph? = null
) {
    fun runtime(): Runtime {
        val streamSourceNodes = findStreamSourceNodes()

        var currentRuntimeName = ""

        streamSourceNodes.forEach { streamSourceNode ->
            val runtimeName = extractFromCommand(streamSourceNode.dataStream)
            if (currentRuntimeName.isBlank()) {
                currentRuntimeName = runtimeName
            } else if (currentRuntimeName != runtimeName) {
                error("multi runtime operation detected: $currentRuntimeName + $runtimeName")
            }
        }

        if (currentRuntimeName.isNotBlank()) {
            return Runtime(currentRuntimeName, KAFKA)
        }

        val shellSourceNodes = findShellSourceNodes()
        if (shellSourceNodes.isNotEmpty() || streamSourceNodes.isEmpty()) {
            return Runtime("shell", SHELL)
        }

        error("could not detect runtime correctly")
    }

    private fun extractFromCommand(dataStream: DataStream): String {
        if (dataStream.path.startsWith(FileSystem.KAFKA_CLUSTERS_PREFIX).not()) {
            return ""
        }

        return dataStream.path.substring(FileSystem.KAFKA_CLUSTERS_PREFIX.length)
            .split("/").filterNot(String::isBlank).firstOrNull() ?: ""
    }

    fun hasStreamSources() = findStreamSourceNodes().isNotEmpty()

    fun hasMoreOutput() =
        graph.findChildren { it.ref is NodeSink && it.ref.output.path.endsWith("-stdout") }.isNotEmpty()

    fun hasRedirections() = graph.findChildren { it.ref is NodeSink }.isNotEmpty()

    private fun findStreamSourceNodes(): Set<NodeStreamSource> = graph
        .findChildren { it.ref is NodeStreamSource }
        .map { it.ref as NodeStreamSource }
        .toSet()

    private fun findShellSourceNodes(): Set<NodeShellSource> = graph
        .findChildren { it.ref is NodeShellSource }
        .map { it.ref as NodeShellSource }
        .toSet()
}
