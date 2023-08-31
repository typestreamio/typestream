package io.typestream.compiler.ast

import io.typestream.graph.Graph


interface NodeResolver<K> {
    fun resolve(): Graph<K>
}
