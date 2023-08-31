package io.typestream.compiler.ast

import io.typestream.compiler.lexer.Token
import io.typestream.compiler.types.DataStream
import io.typestream.compiler.types.datastream.join
import io.typestream.compiler.types.schema.Schema
import io.typestream.compiler.types.schema.empty
import kotlinx.serialization.Serializable

@Serializable
data class ShellCommand(val token: Token, val expressions: List<Expr>) : Command() {
    val dataStreams = mutableListOf<DataStream>()

    override fun inferType(): DataStream {
        if (dataStreams.isEmpty()) return DataStream("/bin/${token.lexeme}", Schema.Struct.empty())
        return dataStreams.reduce { acc, dataStream -> acc.join(dataStream) }
    }

    override fun <K> accept(visitor: Statement.Visitor<K>) = visitor.visitShellCommand(this)

    companion object
}
