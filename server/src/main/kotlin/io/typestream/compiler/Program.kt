package io.typestream.compiler

import io.typestream.compiler.RuntimeType.KAFKA
import io.typestream.compiler.RuntimeType.SHELL
import io.typestream.compiler.node.Node
import io.typestream.compiler.types.DataStream
import io.typestream.filesystem.FileSystem
import io.typestream.graph.Graph

data class Program(val id: String, val graph: Graph<Node>) {
    fun runtime(): Runtime {
        var currentRuntimeName = ""

        streamSourceNodes()
            .forEach { streamSourceNode ->
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

        if (shellSourceNodes().isNotEmpty()) {
            return Runtime("shell", SHELL)
        }

        error("could not detect runtime correctly")
    }

    private fun extractFromCommand(dataStream: DataStream): String {
        return dataStream.path.substring(FileSystem.KAFKA_CLUSTERS_PREFIX.length)
            .split("/").filterNot(String::isBlank).firstOrNull() ?: ""
    }

    fun hasStreamSources() = streamSourceNodes().isNotEmpty()

    fun hasMoreOutput() =
        graph.findChildren { it.ref is Node.Sink && it.ref.output.path.endsWith("-stdout") }.isNotEmpty()

    fun hasRedirections() = graph.findChildren { it.ref is Node.Sink }.isNotEmpty()

    private fun streamSourceNodes(): Set<Node.StreamSource> = graph
        .findChildren { it.ref is Node.StreamSource }
        .map { it.ref as Node.StreamSource }
        .toSet()

    private fun shellSourceNodes(): Set<Node.ShellSource> = graph
        .findChildren { it.ref is Node.ShellSource }
        .map { it.ref as Node.ShellSource }
        .toSet()
}
