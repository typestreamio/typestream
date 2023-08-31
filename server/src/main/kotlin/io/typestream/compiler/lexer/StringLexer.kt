package io.typestream.compiler.lexer

import io.typestream.compiler.lexer.TokenType.STRING_END
import io.typestream.compiler.lexer.TokenType.STRING_INTER_START
import io.typestream.compiler.lexer.TokenType.STRING_LIT_PART

class StringLexer(sourceScanner: SourceScanner) : Tokenizer(sourceScanner) {
    override fun nextToken(stack: ArrayDeque<Tokenizer>): Token = if (!sourceScanner.isAtEnd()) {
        next(stack)
    } else {
        emit(TokenType.EOF)
    }

    private fun next(stack: ArrayDeque<Tokenizer>): Token = when (sourceScanner.next()) {
        '#' -> if (!sourceScanner.match('{')) {
            sourceScanner.emitError()
        } else {
            val interpolationLexer = InterpolationLexer(sourceScanner)
            stack.addLast(interpolationLexer)
            emit(STRING_INTER_START)
        }

        '"' -> {
            stack.removeLast()
            emit(STRING_END)
        }

        else -> stringLiteralPart()
    }

    private fun stringLiteralPart(): Token {
        while (sourceScanner.peek() != '"' && !sourceScanner.isAtEnd()) {
            when (sourceScanner.peek()) {
                '\n' -> sourceScanner.advanceLine()
                '#' -> if (sourceScanner.peekNext() == '{') {
                    return emit(STRING_LIT_PART)
                }
            }
            sourceScanner.next()
        }

        if (sourceScanner.isAtEnd()) {
            return sourceScanner.emitError()
        }
        return emit(STRING_LIT_PART)
    }
}
