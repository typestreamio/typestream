package io.typestream.compiler.ast

import io.typestream.compiler.node.Node
import io.typestream.graph.Graph
import kotlinx.serialization.Serializable

@Serializable
data class Cat(override val expressions: List<Expr>) : DataCommand() {
    override fun resolve(): Graph<Node> {
        require(dataStreams.isNotEmpty()) { "cannot resolve cat command: unbound data streams" }

        return Graph(
            Node.StreamSource(
                toString(),
                dataStreams.first(),
                encoding ?: error("cannot resolve cat command: unbound encoding")
            )
        )
    }
}
