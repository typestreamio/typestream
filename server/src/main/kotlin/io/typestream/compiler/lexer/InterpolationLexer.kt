package io.typestream.compiler.lexer

import io.typestream.compiler.lexer.TokenType.STRING_INTER_END


class InterpolationLexer(sourceScanner: SourceScanner) : Tokenizer(sourceScanner) {
    private val mainLexer = MainLexer(sourceScanner)

    override fun nextToken(stack: ArrayDeque<Tokenizer>): Token {
        return if (!sourceScanner.isAtEnd()) {
            next(stack)
        } else {
            emit(TokenType.EOF)
        }
    }

    fun next(stack: ArrayDeque<Tokenizer>): Token = when (sourceScanner.peek()) {
        '}' -> {
            sourceScanner.next()
            stack.removeLast()
            emit(STRING_INTER_END)
        }

        else -> mainLexer.nextToken(stack)
    }
}
