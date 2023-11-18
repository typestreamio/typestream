package io.typestream.compiler.lexer

import io.typestream.compiler.lexer.TokenType.BAREWORD
import io.typestream.compiler.lexer.TokenType.Companion.keywords
import io.typestream.compiler.lexer.TokenType.EOF
import io.typestream.compiler.lexer.TokenType.ERROR
import io.typestream.compiler.lexer.TokenType.STRING

class SourceScanner(private val source: String, val cursor: CursorPosition? = null) {
    private var line = 1
    private var column = 0
    private var start = 0
    private var current = 0

    fun next(): Char {
        column++
        return source[current++]
    }

    fun advanceLine() {
        line++
        column = 0
    }

    fun peek() = if (isAtEnd()) Char.MIN_VALUE else source[current]

    fun peekNext() = if (current + 1 >= source.length) Char.MIN_VALUE else source[current + 1]

    fun isAtEnd() = current >= source.length

    fun match(c: Char): Boolean {
        if (isAtEnd()) return false
        if (source[current] != c) return false
        current++
        return true
    }

    fun previous(): Char = source[current - 1]

    //TODO add description
    fun emitError() = emitToken(ERROR)

    fun emitToken(type: TokenType): Token {
        val lexeme = source.substring(start, current).replace("\\\"", "\"")
        val token = when (type) {
            EOF -> Token(type, "", line, column)
            STRING -> Token(type, lexeme.substring(1, lexeme.length - 1), line, column)
            BAREWORD -> Token(keywords[lexeme] ?: BAREWORD, lexeme, line, column)
            else -> Token(type, lexeme, line, column)
        }

        syncStart()

        return token
    }

    fun syncStart() {
        start = current
    }

    override fun toString() = buildString {
        append("SourceScanner(")
        append("source=$source, ")
        append("currentLexeme=${source.substring(start, current)}, ")
    }

}
