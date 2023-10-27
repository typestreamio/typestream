package io.typestream.compiler.ast

import io.typestream.compiler.node.Node
import io.typestream.graph.Graph
import kotlinx.serialization.Serializable

//TODO add support for key based filtering
@Serializable
data class Grep(override val expressions: List<Expr>) : DataCommand() {
    val predicates = mutableListOf<Predicate>()

    override fun resolve(): Graph<Node> {
        if (predicates.isNotEmpty()) {
            return Graph(Node.Filter(toString(), predicates.stream().reduce(Predicate::and).get()))
        }

        val predicate = Predicate.matches(boundArgs.first())

        return when (dataStreams.size) {
            0 -> Graph(Node.Filter(toString(), predicate))

            1 -> {
                val streamSourceNode: Graph<Node> = Graph(
                    Node.StreamSource(
                        toString(),
                        dataStreams.first(),
                        encoding ?: error("cannot resolve grep command: unbound encoding")
                    )
                )

                streamSourceNode.addChild(Graph(Node.Filter(toString(), predicate)))
                streamSourceNode
            }

            else -> throw IllegalArgumentException("invalid number of arguments")
        }
    }
}
