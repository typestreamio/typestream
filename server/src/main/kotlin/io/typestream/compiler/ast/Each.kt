package io.typestream.compiler.ast

import io.typestream.compiler.node.KeyValue
import io.typestream.compiler.node.Node
import io.typestream.compiler.types.Value
import io.typestream.graph.Graph
import kotlinx.serialization.Serializable

@Serializable
data class Each(override val expressions: List<Expr>) : DataCommand() {
    var block: Value.Block? = null

    override fun resolve(): Graph<Node> {
        val boundBlock = block
        requireNotNull(boundBlock) { "cannot resolve each node: unbound block" }

        return Graph(Node.Each(toString()) { keyValue ->
            KeyValue(keyValue.key, boundBlock.value.invoke(keyValue.value))
        })
    }
}
