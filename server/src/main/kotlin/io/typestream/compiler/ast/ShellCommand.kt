package io.typestream.compiler.ast

import io.typestream.compiler.lexer.Token
import io.typestream.compiler.types.DataStream
import kotlinx.serialization.Serializable

@Serializable
data class ShellCommand(val token: Token, val expressions: List<Expr>) : Command() {
    val dataStreams = mutableListOf<DataStream>()

    override fun <K> accept(visitor: Statement.Visitor<K>) = visitor.visitShellCommand(this)

    companion object
}
