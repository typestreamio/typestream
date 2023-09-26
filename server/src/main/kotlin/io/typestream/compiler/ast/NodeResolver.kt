package io.typestream.compiler.ast

import io.typestream.graph.Graph

sealed interface NodeResolver<K> {
    fun resolve(): Graph<K>
}
