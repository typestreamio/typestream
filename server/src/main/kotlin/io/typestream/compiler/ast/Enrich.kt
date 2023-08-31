package io.typestream.compiler.ast

import io.typestream.compiler.node.KeyValue
import io.typestream.compiler.node.Node
import io.typestream.compiler.types.Value
import io.typestream.graph.Graph
import kotlinx.serialization.Serializable

@Serializable
data class Enrich(override val expressions: List<Expr>) : DataCommand() {
    var block: Value.Block? = null
    val expressionBlock = expressions.first() as Expr.Block

    override fun inferType() = io.typestream.compiler.types.inferType(expressionBlock.pipeline.commands)

    override fun resolve(): Graph<Node> {
        val boundBlock = block
        requireNotNull(boundBlock) { "cannot resolve enrich node: unbound block" }

        return Graph(Node.Map(toString()) { keyValue ->
            KeyValue(keyValue.key, boundBlock.value.invoke(keyValue.value))
        })
    }
}
