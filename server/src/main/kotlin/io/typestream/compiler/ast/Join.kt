package io.typestream.compiler.ast

import io.typestream.compiler.node.JoinType
import io.typestream.compiler.node.Node
import io.typestream.graph.Graph
import kotlinx.serialization.Serializable

//TODO support lookup syntax
//TODO support multiple data streams
@Serializable
data class Join(override val expressions: List<Expr>) : DataCommand() {
    override fun resolve(): Graph<Node> = Graph(Node.Join(toString(), dataStreams.first(), JoinType()))
}
