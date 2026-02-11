package io.typestream.compiler.ast

import io.typestream.compiler.node.Node
import io.typestream.graph.Graph
import io.typestream.option.Option
import io.typestream.option.parseOptions
import kotlinx.serialization.Serializable

@Serializable
data class Grep(override val expressions: List<Expr>) : DataCommand() {
    data class Options(
        @param:Option(
            names = ["-v", "--invert-match"],
            description = "inverts matching pattern"
        ) val invertMatch: Boolean = false,
        @param:Option(names = ["-k", "--by-key"], description = "matches key only") val byKey: Boolean = false,
    )

    val predicates = mutableListOf<Predicate>()
    var rawPredicateExpr: String? = null

    override fun resolve(): Graph<Node> {
        val (options, args) = parseOptions<Options>(boundArgs)

        var predicate =
            if (predicates.isNotEmpty()) predicates.reduce(Predicate::and) else Predicate.matches(args.first())

        if (options.invertMatch) {
            predicate = predicate.not()
        }

        return when (dataStreams.size) {
            0 -> Graph(Node.Filter(toString(), options.byKey, predicate))

            1 -> {
                Graph<Node>(
                    Node.StreamSource(
                        toString(), dataStreams.first(),
                        encoding ?: error("cannot resolve grep command: unbound encoding")
                    )
                ).also { it.addChild(Graph(Node.Filter(toString(), options.byKey, predicate))) }
            }

            else -> throw IllegalArgumentException("cat does not support multiple data streams")
        }
    }
}
