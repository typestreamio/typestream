package io.typestream.compiler

import io.typestream.compiler.Runtime.Companion.extractFrom
import io.typestream.compiler.node.Node
import io.typestream.graph.Graph

data class Program(val id: String, val graph: Graph<Node>) {
    fun runtime(): Runtime {
        val streamSourceNodes = findStreamSourceNodes()

        val runtimes = streamSourceNodes.mapNotNull { extractFrom(it.dataStream) }.toMutableSet()

        val shellSourceNodes = findShellSourceNodes()
        if (shellSourceNodes.isNotEmpty()) {
            runtimes.add(Runtime("shell", Runtime.Type.SHELL))
        }

        if (shellSourceNodes.isEmpty() && streamSourceNodes.isEmpty()) {
            return Runtime("shell", Runtime.Type.SHELL)
        }

        return when (runtimes.size) {
            1 -> runtimes.first()
            2 -> runtimes.firstOrNull { it.type == Runtime.Type.MEMORY }
                ?: error("multi runtime operation detected: ${runtimes.map(Runtime::name).joinToString(" + ")}")

            else -> error("could not detect runtime correctly")
        }
    }

    fun hasStreamSources(): Boolean = findStreamSourceNodes().isNotEmpty()

    fun hasMoreOutput(): Boolean =
        graph.findChildren { it.ref is Node.Sink && it.ref.output.path.endsWith("-stdout") }.isNotEmpty()

    fun hasRedirections(): Boolean = graph.findChildren { it.ref is Node.Sink }.isNotEmpty()

    private fun findStreamSourceNodes(): Set<Node.StreamSource> = graph
        .findChildren { it.ref is Node.StreamSource }
        .map { it.ref as Node.StreamSource }
        .toSet()

    private fun findShellSourceNodes(): Set<Node.ShellSource> = graph
        .findChildren { it.ref is Node.ShellSource }
        .map { it.ref as Node.ShellSource }
        .toSet()
}
