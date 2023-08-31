package io.typestream.compiler.lexer

import io.typestream.compiler.lexer.TokenType.*


class MainLexer(sourceScanner: SourceScanner) : Tokenizer(sourceScanner) {
    override fun nextToken(stack: ArrayDeque<Tokenizer>): Token = if (!sourceScanner.isAtEnd()) {
        next(stack)
    } else {
        emit(EOF)
    }

    private fun next(stack: ArrayDeque<Tokenizer>): Token = when (val c = sourceScanner.next()) {
        ' ', '\r', '\t' -> {
            sourceScanner.syncStart()
            nextToken(stack)
        }

        ';' -> emit(SEMICOLON)
        '\n' -> {
            sourceScanner.advanceLine()
            emit(NEWLINE)
        }

        '!' -> emit(if (sourceScanner.match('=')) BANG_EQUAL else BANG)
        '=' -> emit(if (sourceScanner.match('=')) EQUAL_EQUAL else EQUAL)
        '~' -> if (sourceScanner.match('=')) emit(ALMOST_EQUAL) else sourceScanner.emitError()
        '|' -> emit(if (sourceScanner.match('|')) OR else PIPE)
        '&' -> if (sourceScanner.match('&')) emit(AND) else sourceScanner.emitError()
        '$' -> emit(DOLLAR)
        '{' -> emit(LEFT_CURLY)
        '}' -> emit(RIGHT_CURLY)
        '[' -> emit(LEFT_BRACKET)
        ']' -> emit(RIGHT_BRACKET)
        '(' -> emit(LEFT_PAREN)
        ')' -> emit(RIGHT_PAREN)
        '-' -> if (sourceScanner.match('>')) emit(LAMBDA_ARROW) else bareWord()
        '<' -> emit(if (sourceScanner.match('=')) LESS_EQUAL else LESS)
        '>' -> when (sourceScanner.peek()) {
            '>' -> {
                sourceScanner.next()
                emit(GREATER_GREATER)
            }

            '=' -> {
                sourceScanner.next()
                emit(GREATER_EQUAL)
            }

            else -> emit(GREATER)
        }

        '\'' -> stringLiteral()
        '"' -> {
            val stringLexer = StringLexer(sourceScanner)
            stack.addLast(stringLexer)
            emit(STRING_START)
        }

        else -> {
            if (isDigit(c)) {
                number()
            } else if (isBareWord(c)) {
                bareWord()
            } else {
                sourceScanner.emitError()
            }
        }
    }

    private fun number(): Token {
        while (isDigit(sourceScanner.peek())) sourceScanner.next()

        if (sourceScanner.peek() == '.' && isDigit(sourceScanner.peekNext())) {
            sourceScanner.next()

            while (isDigit(sourceScanner.peek())) sourceScanner.next()
        }

        return emit(NUMBER)
    }

    private fun bareWord(): Token {
        while (isBareWord(sourceScanner.peek())) sourceScanner.next()

        return emit(BAREWORD)
    }

    private fun stringLiteral(): Token {
        val quoteType = sourceScanner.previous()
        while (sourceScanner.peek() != quoteType && !sourceScanner.isAtEnd()) {
            if (sourceScanner.peek() == '\n') {
                sourceScanner.advanceLine()
            }
            sourceScanner.next()
        }

        if (sourceScanner.isAtEnd()) {
            return sourceScanner.emitError()
        }

        sourceScanner.next()

        return emit(STRING)
    }
}
