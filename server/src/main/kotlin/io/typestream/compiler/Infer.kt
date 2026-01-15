package io.typestream.compiler

import io.typestream.compiler.node.Node
import io.typestream.compiler.types.DataStream
import io.typestream.compiler.types.TypeRules
import io.typestream.compiler.types.datastream.join
import io.typestream.graph.Graph

object Infer {
    fun infer(graph: Graph<Node>) {
        infer(graph, null)
    }

    private fun infer(node: Graph<Node>, input: DataStream?) {
        val output = when (val ref = node.ref) {
            is Node.NoOp -> input
            is Node.StreamSource -> ref.dataStream
            is Node.ShellSource -> {
                require(ref.data.isNotEmpty()) { "shell source ${ref.id} has no data streams" }
                ref.data.singleOrNull() ?: error("cannot infer shell source with multiple streams yet")
            }

            is Node.Filter -> {
                val stream = input ?: error("filter ${ref.id} missing input stream")
                val errors = ref.predicate.typeCheck(stream)
                require(errors.isEmpty()) { errors.joinToString("\n") }
                stream
            }

            is Node.Map -> input ?: error("map ${ref.id} missing input stream")
            is Node.Join -> {
                val stream = input ?: error("join ${ref.id} missing input stream")
                stream.join(ref.with)
            }

            is Node.Group -> input ?: error("group ${ref.id} missing input stream")
            is Node.Count -> input ?: error("count ${ref.id} missing input stream")
            is Node.ReduceLatest -> input ?: error("reduceLatest ${ref.id} missing input stream")
            is Node.Each -> input ?: error("each ${ref.id} missing input stream")
            is Node.Sink -> input ?: error("sink ${ref.id} missing input stream")
            is Node.GeoIp -> {
                val stream = input ?: error("geoIp ${ref.id} missing input stream")
                TypeRules.inferGeoIp(stream, ref.ipField, ref.outputField)
            }
            is Node.TextExtractor -> {
                val stream = input ?: error("textExtractor ${ref.id} missing input stream")
                TypeRules.inferTextExtractor(stream, ref.filePathField, ref.outputField)
            }
            is Node.EmbeddingGenerator -> {
                val stream = input ?: error("embeddingGenerator ${ref.id} missing input stream")
                TypeRules.inferEmbeddingGenerator(stream, ref.textField, ref.outputField)
            }
            is Node.Inspector -> input ?: error("inspector ${ref.id} missing input stream")
        }

        node.children.forEach { infer(it, output) }
    }
}
