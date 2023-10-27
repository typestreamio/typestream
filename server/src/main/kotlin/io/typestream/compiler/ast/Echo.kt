package io.typestream.compiler.ast

import io.typestream.compiler.node.Node
import io.typestream.compiler.types.DataStream
import io.typestream.compiler.types.schema.Schema
import io.typestream.compiler.types.schema.empty
import io.typestream.graph.Graph
import kotlinx.serialization.Serializable

@Serializable
data class Echo(override val expressions: List<Expr>) : DataCommand() {
    override fun resolve(): Graph<Node> {
        val outputStreams = mutableListOf<DataStream>()

        outputStreams.addAll(dataStreams)

        outputStreams.addAll(boundArgs.map { DataStream("/bin/echo", Schema.String(it)) })

        return Graph(Node.ShellSource(toString(), outputStreams))
    }
}
