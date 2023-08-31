package io.typestream.compiler.lexer

import kotlinx.serialization.Serializable

@Serializable
data class Token(val type: TokenType, val lexeme: String, val line: Int, val column: Int)
