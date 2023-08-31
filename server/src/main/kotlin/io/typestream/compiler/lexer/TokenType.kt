package io.typestream.compiler.lexer

import kotlinx.serialization.Serializable

@Serializable
enum class TokenType {
    //keywords
    CAT, CUT, ECHO, ENRICH, GREP, JOIN, LET, WC,

    BANG, BANG_EQUAL,
    EQUAL, EQUAL_EQUAL,
    ALMOST_EQUAL,
    LESS, LESS_EQUAL,
    GREATER, GREATER_GREATER, GREATER_EQUAL,
    PIPE,
    OR, AND,
    PLUS,

    LAMBDA_ARROW,
    LEFT_CURLY, RIGHT_CURLY,
    LEFT_BRACKET, RIGHT_BRACKET,
    LEFT_PAREN, RIGHT_PAREN,

    SEMICOLON, NEWLINE,
    DOLLAR,

    BAREWORD,
    INCOMPLETE,

    STRING_START, STRING_LIT_PART, STRING_INTER_START, STRING_INTER_END, STRING_END,
    STRING, NUMBER,

    EOF, ERROR;

    companion object {
        val keywords = mapOf(
            "cat" to CAT,
            "cut" to CUT,
            "echo" to ECHO,
            "enrich" to ENRICH,
            "grep" to GREP,
            "join" to JOIN,
            "let" to LET,
            "wc" to WC
        )
    }
}
