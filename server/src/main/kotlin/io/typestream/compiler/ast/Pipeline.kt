package io.typestream.compiler.ast

import io.typestream.compiler.types.Encoding
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class Pipeline(val commands: List<Command>, val redirections: List<Redirection> = listOf()) : Statement {
    var encoding: Encoding? = null

    override fun <K> accept(visitor: Statement.Visitor<K>) = visitor.visitPipeline(this)

    fun clone(): Pipeline = Json.decodeFromString(Json.encodeToString(this))
}
