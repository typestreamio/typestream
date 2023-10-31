package io.typestream.compiler.ast

import io.typestream.compiler.node.KeyValue
import io.typestream.compiler.node.Node
import io.typestream.graph.Graph
import io.typestream.option.Option
import io.typestream.option.parseOptions
import kotlinx.serialization.Serializable

@Serializable
data class Wc(override val expressions: List<Expr>) : DataCommand() {
    data class Options(
        @param:Option(names = ["-b", "--by"], description = "counts by selected field") val by: String,
    )

    override fun resolve(): Graph<Node> {
        val (options, _) = parseOptions()

        val mapper = if (options.by.isBlank()) {
            { keyValue: KeyValue -> keyValue.key }
        } else {
            { keyValue: KeyValue -> keyValue.value.select(listOf(options.by)) }
        }

        val groupNode: Graph<Node> = Graph(Node.Group(toString(), mapper))

        groupNode.addChild(Graph(Node.Count(toString())))

        return groupNode
    }

    fun parseOptions() = parseOptions<Options>(boundArgs)
}
