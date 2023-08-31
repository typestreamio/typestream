package io.typestream.compiler.lexer

import io.typestream.compiler.lexer.TokenType.*
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.groups.Tuple.tuple
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

private fun ModalLexer.tokens() = buildList {
    var token = next()
    while (token.type != EOF) {
        add(token)
        token = next()
    }
}

internal class ModalLexerTest {

    @Test
    fun `scans command with filesystem paths`() {
        val lexer = ModalLexer(SourceScanner("cd dev/kafka"))

        assertThat(lexer.tokens()).extracting("type", "lexeme").containsExactly(
            tuple(BAREWORD, "cd"),
            tuple(BAREWORD, "dev/kafka"),
        )
    }

    @Test
    fun `scans commands with options`() {
        val lexer = ModalLexer(SourceScanner("grep isTheAnswer42 topic --from-beginning"))

        assertThat(lexer.tokens()).extracting("type", "lexeme").containsExactly(
            tuple(GREP, "grep"),
            tuple(BAREWORD, "isTheAnswer42"),
            tuple(BAREWORD, "topic"),
            tuple(BAREWORD, "--from-beginning"),
        )
    }

    @Test
    fun `scans pipes`() {
        val lexer = ModalLexer(SourceScanner("cat topic | grep something | cut something-else"))

        val tokens = lexer.tokens()
        assertThat(tokens).extracting("type", "lexeme").containsExactly(
            tuple(CAT, "cat"),
            tuple(BAREWORD, "topic"),
            tuple(PIPE, "|"),
            tuple(GREP, "grep"),
            tuple(BAREWORD, "something"),
            tuple(PIPE, "|"),
            tuple(CUT, "cut"),
            tuple(BAREWORD, "something-else"),
        )
    }

    @Test
    fun `scans redirect`() {
        val lexer = ModalLexer(SourceScanner("cat topic > something"))

        val tokens = lexer.tokens()
        assertThat(tokens).extracting("type", "lexeme").containsExactly(
            tuple(CAT, "cat"),
            tuple(BAREWORD, "topic"),
            tuple(GREATER, ">"),
            tuple(BAREWORD, "something"),
        )
    }

    @Test
    fun `scans append`() {
        val lexer = ModalLexer(SourceScanner("cat topic >> something"))

        val tokens = lexer.tokens()
        assertThat(tokens).extracting("type", "lexeme").containsExactly(
            tuple(CAT, "cat"),
            tuple(BAREWORD, "topic"),
            tuple(GREATER_GREATER, ">>"),
            tuple(BAREWORD, "something"),
        )
    }

    @Test
    fun `scans statements`() {
        val lexer = ModalLexer(SourceScanner("cat topic; grep something 42"))

        val tokens = lexer.tokens()
        assertThat(tokens).extracting("type", "lexeme").containsExactly(
            tuple(CAT, "cat"),
            tuple(BAREWORD, "topic"),
            tuple(SEMICOLON, ";"),
            tuple(GREP, "grep"),
            tuple(BAREWORD, "something"),
            tuple(NUMBER, "42"),
        )
    }

    @Test
    fun `scans multiple lines`() {
        val lexer = ModalLexer(SourceScanner("cat topic\ngrep something 42"))

        val tokens = lexer.tokens()
        assertThat(tokens).extracting("type", "lexeme").containsExactly(
            tuple(CAT, "cat"),
            tuple(BAREWORD, "topic"),
            tuple(NEWLINE, "\n"),
            tuple(GREP, "grep"),
            tuple(BAREWORD, "something"),
            tuple(NUMBER, "42"),
        )
    }

    @Nested
    inner class Errors {
        @Test
        fun `detects errors`() {
            val lexer = ModalLexer(SourceScanner("cat topic + another"))


            assertThat(lexer.tokens()).contains(Token(ERROR, "+", 1, 11))
        }

        @Test
        fun `detects errors on multiple lines`() {
            val lexer = ModalLexer(SourceScanner("cat topic + another\ncat + something else"))

            assertThat(lexer.tokens()).contains(
                Token(ERROR, "+", 1, 11),
                Token(ERROR, "+", 2, 5),
            )
        }
    }

    @Nested
    inner class Numbers {
        @Test
        fun `scans number`() {
            val lexer = ModalLexer(SourceScanner("cat 42"))

            assertThat(lexer.tokens()).extracting("type", "lexeme").containsExactly(
                tuple(CAT, "cat"), tuple(NUMBER, "42"),
            )
        }

        @Test
        fun `scans double`() {
            val lexer = ModalLexer(SourceScanner("cat 42.24"))

            assertThat(lexer.tokens()).extracting("type", "lexeme").containsExactly(
                tuple(CAT, "cat"), tuple(NUMBER, "42.24")
            )
        }
    }

    @Nested
    inner class Strings {
        @Test
        fun `scans double quoted strings`() {
            val lexer = ModalLexer(SourceScanner("cat \"topic\""))

            assertThat(lexer.tokens()).extracting("type", "lexeme").containsExactly(
                tuple(CAT, "cat"),
                tuple(STRING_START, "\""),
                tuple(STRING_LIT_PART, "topic"),
                tuple(STRING_END, "\"")
            )
        }

        @Test
        fun `scans string interpolation`() {
            val lexer = ModalLexer(SourceScanner("cat \"topic_#{\$foo}\""))

            assertThat(lexer.tokens()).extracting("type", "lexeme").containsExactly(
                tuple(CAT, "cat"),
                tuple(STRING_START, "\""),
                tuple(STRING_LIT_PART, "topic_"),
                tuple(STRING_INTER_START, "#{"),
                tuple(DOLLAR, "$"),
                tuple(BAREWORD, "foo"),
                tuple(STRING_INTER_END, "}"),
                tuple(STRING_END, "\"")
            )
        }

        @Test
        fun `scans single quoted strings`() {
            val lexer = ModalLexer(SourceScanner("cat 'topic'"))

            assertThat(lexer.tokens()).extracting("type", "lexeme").containsExactly(
                tuple(CAT, "cat"), tuple(STRING, "topic")
            )
        }
    }

    @Nested
    inner class Variables {
        @Test
        fun `scans strings assignment`() {
            val lexer = ModalLexer(SourceScanner("let foo=\"bar\""))

            assertThat(lexer.tokens()).extracting("type", "lexeme").containsExactly(
                tuple(LET, "let"),
                tuple(BAREWORD, "foo"),
                tuple(EQUAL, "="),
                tuple(STRING_START, "\""),
                tuple(STRING_LIT_PART, "bar"),
                tuple(STRING_END, "\""),
            )
        }
    }

    @Nested
    inner class Conditions {
        @Test
        fun `scans simple condition`() {
            val lexer = ModalLexer(SourceScanner("grep [ .text == \"answer\" ]"))

            assertThat(lexer.tokens()).extracting("type", "lexeme").containsExactly(
                tuple(GREP, "grep"),
                tuple(LEFT_BRACKET, "["),
                tuple(BAREWORD, ".text"),
                tuple(EQUAL_EQUAL, "=="),
                tuple(STRING_START, "\""),
                tuple(STRING_LIT_PART, "answer"),
                tuple(STRING_END, "\""),
                tuple(RIGHT_BRACKET, "]"),
            )
        }

        @Test
        fun `scans grouped condition`() {
            val lexer = ModalLexer(
                SourceScanner("grep [ (.text == \"answer\" || .text == \"42\") && .text != \"something\" ]")
            )

            assertThat(lexer.tokens()).extracting("type", "lexeme").containsExactly(
                tuple(GREP, "grep"),
                tuple(LEFT_BRACKET, "["),
                tuple(LEFT_PAREN, "("),
                tuple(BAREWORD, ".text"),
                tuple(EQUAL_EQUAL, "=="),
                tuple(STRING_START, "\""),
                tuple(STRING_LIT_PART, "answer"),
                tuple(STRING_END, "\""),
                tuple(OR, "||"),
                tuple(BAREWORD, ".text"),
                tuple(EQUAL_EQUAL, "=="),
                tuple(STRING_START, "\""),
                tuple(STRING_LIT_PART, "42"),
                tuple(STRING_END, "\""),
                tuple(RIGHT_PAREN, ")"),
                tuple(AND, "&&"),
                tuple(BAREWORD, ".text"),
                tuple(BANG_EQUAL, "!="),
                tuple(STRING_START, "\""),
                tuple(STRING_LIT_PART, "something"),
                tuple(STRING_END, "\""),
                tuple(RIGHT_BRACKET, "]"),
            )
        }
    }
}
