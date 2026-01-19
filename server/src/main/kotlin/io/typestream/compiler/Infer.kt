package io.typestream.compiler

import io.typestream.compiler.node.Node
import io.typestream.compiler.types.DataStream
import io.typestream.compiler.types.Encoding
import io.typestream.compiler.types.InferenceContext
import io.typestream.graph.Graph

/**
 * No-op inference context for validation.
 * During Infer validation, nodes already have their data resolved,
 * so we don't need actual catalog access.
 */
private object NoOpInferenceContext : InferenceContext {
    override fun lookupDataStream(path: String): DataStream {
        error("Catalog lookup not available during validation: $path")
    }

    override fun lookupEncoding(path: String): Encoding {
        error("Encoding lookup not available during validation: $path")
    }
}

object Infer {
    fun infer(graph: Graph<Node>) {
        infer(graph, null, null)
    }

    private fun infer(node: Graph<Node>, input: DataStream?, inputEncoding: Encoding?) {
        val ref = node.ref

        // Special handling for Filter to validate predicate type checking
        if (ref is Node.Filter && input != null) {
            val errors = ref.predicate.typeCheck(input)
            require(errors.isEmpty()) { errors.joinToString("\n") }
        }

        // Use the node's inferOutputSchema method for type inference
        val result = ref.inferOutputSchema(input, inputEncoding, NoOpInferenceContext)

        // Recursively validate children
        node.children.forEach { infer(it, result.dataStream, result.encoding) }
    }
}
