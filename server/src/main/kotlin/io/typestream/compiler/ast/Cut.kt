package io.typestream.compiler.ast

import io.typestream.compiler.node.KeyValue
import io.typestream.compiler.node.Node
import io.typestream.compiler.node.NodeMap
import io.typestream.graph.Graph
import kotlinx.serialization.Serializable

//TODO should also support non piped commands?
@Serializable
data class Cut(override val expressions: List<Expr>) : DataCommand() {
    override fun resolve(): Graph<Node> = Graph(NodeMap(toString()) { keyValue ->
        KeyValue(keyValue.key, keyValue.value.select(boundArgs))
    })
}
