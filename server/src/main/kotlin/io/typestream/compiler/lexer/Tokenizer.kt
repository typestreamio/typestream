package io.typestream.compiler.lexer

abstract class Tokenizer(val sourceScanner: SourceScanner) {
    abstract fun nextToken(stack: ArrayDeque<Tokenizer>): Token

    fun isDigit(c: Char) = c in '0'..'9'

    private fun isAlpha(c: Char) =
        c in 'a'..'z' || c in 'A'..'Z' || c == '_' || c == '-' || c == '/' || c == '.' || c == ':'

    fun isBareWord(c: Char) = isDigit(c) || isAlpha(c)

    fun emit(type: TokenType) = sourceScanner.emitToken(type)
}
