package io.typestream.compiler.ast

import io.typestream.compiler.types.Encoding
import kotlinx.serialization.Serializable

@Serializable
sealed class Command : Statement {
    val boundArgs = mutableListOf<String>()
    var encoding: Encoding? = null
}
