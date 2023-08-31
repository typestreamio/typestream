package io.typestream.compiler.ast

import io.typestream.compiler.node.Node
import io.typestream.compiler.types.DataStream
import kotlinx.serialization.Serializable

@Serializable
sealed class DataCommand : Command(), NodeResolver<Node> {
    abstract val expressions: List<Expr>

    val dataStreams = mutableListOf<DataStream>()

    override fun <K> accept(visitor: Statement.Visitor<K>) = visitor.visitDataCommand(this)
}
