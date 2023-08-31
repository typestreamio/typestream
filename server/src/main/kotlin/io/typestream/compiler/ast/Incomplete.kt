package io.typestream.compiler.ast

import io.typestream.compiler.lexer.Token
import io.typestream.compiler.node.Node
import io.typestream.compiler.types.DataStream
import io.typestream.compiler.types.schema.Schema
import io.typestream.compiler.types.schema.empty
import io.typestream.graph.Graph
import kotlinx.serialization.Serializable

@Serializable
data class Incomplete(val token: Token, override val expressions: List<Expr> = listOf()) : DataCommand() {
    override fun inferType() = DataStream("/bin/noop", Schema.Struct.empty())

    override fun resolve(): Graph<Node> = Graph(Node.NoOp(toString()))
}

