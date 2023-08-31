package io.typestream.compiler.ast

import io.typestream.compiler.lexer.TokenType
import io.typestream.compiler.types.DataStream
import kotlinx.serialization.Serializable

@Serializable
data class Redirection(val type: TokenType, val word: Expr) {
    var dataStream: DataStream? = null
    override fun toString() = "$type ${printWord()}"

    private fun printWord() = buildString {
        if (dataStream != null) {
            append("dataStream: [$dataStream] ")
        }
        append("[$word]")
    }
}
