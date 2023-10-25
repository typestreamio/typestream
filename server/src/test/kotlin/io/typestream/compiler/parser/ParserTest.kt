package io.typestream.compiler.parser

import io.typestream.compiler.ast.Cat
import io.typestream.compiler.ast.Command
import io.typestream.compiler.ast.Cut
import io.typestream.compiler.ast.DataCommand
import io.typestream.compiler.ast.Enrich
import io.typestream.compiler.ast.Expr
import io.typestream.compiler.ast.Grep
import io.typestream.compiler.ast.Join
import io.typestream.compiler.ast.Pipeline
import io.typestream.compiler.ast.Redirection
import io.typestream.compiler.ast.ShellCommand
import io.typestream.compiler.ast.Statement
import io.typestream.compiler.ast.VarDeclaration
import io.typestream.compiler.ast.Wc
import io.typestream.compiler.lexer.Token
import io.typestream.compiler.lexer.TokenType.AND
import io.typestream.compiler.lexer.TokenType.BANG_EQUAL
import io.typestream.compiler.lexer.TokenType.BAREWORD
import io.typestream.compiler.lexer.TokenType.EQUAL_EQUAL
import io.typestream.compiler.lexer.TokenType.GREATER
import io.typestream.compiler.lexer.TokenType.GREATER_GREATER
import io.typestream.compiler.lexer.TokenType.OR
import io.typestream.compiler.lexer.TokenType.PLUS
import io.typestream.compiler.types.Value
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.tuple
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.util.stream.Stream

fun extractCommand(statements: List<Statement>): Command {
    val pipeline = statements[0]
    assertThat(pipeline).isInstanceOf(Pipeline::class.java)
    require(pipeline is Pipeline)
    assertThat(pipeline.commands).hasSize(1)
    return pipeline.commands[0]
}

internal class ParserTest {

    companion object {
        @JvmStatic
        fun dataCommands(): Stream<Arguments> = Stream.of(
            Arguments.of("cat", Cat::class.java),
            Arguments.of("cut", Cut::class.java),
            Arguments.of("grep", Grep::class.java),
            Arguments.of("join", Join::class.java),
            Arguments.of("wc", Wc::class.java)
        )
    }

    @ParameterizedTest
    @MethodSource("dataCommands")
    fun `parses dataCommand`(cmd: String, klass: Class<out DataCommand>) {
        val parser = Parser("$cmd topic")

        val statements = parser.parse()
        assertThat(parser.errors).hasSize(0)
        assertThat(statements).hasSize(1)

        val command = extractCommand(statements)
        require(command is DataCommand) { "expected a DataCommand" }
        assertThat(command).isInstanceOf(klass)
        assertThat(command.expressions).isEqualTo(listOf(Expr.BareWord("topic")))
    }


    // "Sentinel" test to make sure we synchronize correctly inside quotes
    @Test
    fun `parses a quote`() {
        val parser = Parser("\"hello\"")

        parser.parse()
        assertThat(parser.errors).hasSize(1)
    }

    @Test
    fun `parses a shell command`() {
        val parser = Parser("cd dev/kafka")

        val statements = parser.parse()
        assertThat(parser.errors).hasSize(0)
        assertThat(statements).hasSize(1)

        val command = statements[0]
        assertThat(command).isInstanceOf(Pipeline::class.java)
        require(command is Pipeline)
        assertThat(command.commands)
            .hasSize(1)
            .containsExactly(ShellCommand(Token(BAREWORD, "cd", 1, 2), listOf(Expr.BareWord("dev/kafka"))))
    }

    @Test
    fun `parses a string var declaration`() {
        val parser = Parser("let foo = \"bar\"")

        val statements = parser.parse()
        assertThat(parser.errors).hasSize(0)
        assertThat(statements).hasSize(1)

        val command = statements[0]
        assertThat(command).isInstanceOf(VarDeclaration::class.java)
        assertThat(command)
            .extracting("token.lexeme", "expr.value")
            .containsExactly("foo", Expr.Literal(Value.String("bar")))
    }

    @Test
    fun `parses a bare word var declaration`() {
        val parser = Parser("let foo = bar")

        val statements = parser.parse()
        assertThat(parser.errors).hasSize(0)
        assertThat(statements).hasSize(1)

        val command = statements[0]
        assertThat(command).isInstanceOf(VarDeclaration::class.java)
        assertThat(command)
            .extracting("token.lexeme", "expr.value")
            .containsExactly("foo", Expr.BareWord("bar"))
    }

    @Test
    fun `parses a redirect command`() {
        val parser = Parser("cat whatever > file")

        val statements = parser.parse()
        assertThat(parser.errors).hasSize(0)
        assertThat(statements).hasSize(1)

        val pipeline = statements[0]
        assertThat(pipeline).isInstanceOf(Pipeline::class.java)
        require(pipeline is Pipeline)
        assertThat(pipeline.commands).hasSize(1)
        val command = pipeline.commands[0]
        assertThat(command).isInstanceOf(DataCommand::class.java)
        assertThat(command).isEqualTo((Cat(listOf(Expr.BareWord("whatever")))))

        assertThat(pipeline.redirections).extracting("type", "word")
            .containsExactly(tuple(GREATER, Expr.BareWord("file")))
    }

    @Test
    fun `parses an append command`() {
        val parser = Parser("cat whatever >> file")

        val statements = parser.parse()
        assertThat(parser.errors).hasSize(0)
        assertThat(statements).hasSize(1)

        val pipeline = statements[0]
        assertThat(pipeline).isInstanceOf(Pipeline::class.java)
        require(pipeline is Pipeline)
        assertThat(pipeline.commands).hasSize(1)
        val command = pipeline.commands[0]
        assertThat(command).isEqualTo((Cat(listOf(Expr.BareWord("whatever")))))

        assertThat(pipeline.redirections)
            .extracting("type", "word")
            .containsExactly(tuple(GREATER_GREATER, Expr.BareWord("file")))
    }

    @Test
    fun `parses a pipe command`() {
        val parser = Parser("cat answer | grep 42")

        val statements = parser.parse()
        assertThat(parser.errors).hasSize(0)
        assertThat(statements).hasSize(1)

        val command = statements[0]
        assertThat(command).isInstanceOf(Pipeline::class.java)

        val pipeline = command as Pipeline

        assertThat(pipeline.commands)
            .containsExactly(
                Cat(listOf(Expr.BareWord("answer"))),
                Grep(listOf(Expr.Literal(Value.Number(42.0))))
            )
    }

    @Test
    fun `parses a pipe chain command`() {
        val parser = Parser("cat answer | grep 42 | cut -d1")

        val statements = parser.parse()
        assertThat(parser.errors).hasSize(0)
        assertThat(statements).hasSize(1)

        val command = statements[0]
        assertThat(command).isInstanceOf(Pipeline::class.java)
        val pipeline = command as Pipeline

        assertThat(pipeline.commands)
            .containsExactly(
                Cat(listOf(Expr.BareWord("answer"))),
                Grep(listOf(Expr.Literal(Value.Number(42.0)))),
                Cut(listOf(Expr.BareWord("-d1")))
            )
    }

    @Test
    fun `parses a pipe chained with a redirect command`() {
        val parser = Parser("cat answer | grep 42 > result")

        val statements = parser.parse()
        assertThat(parser.errors).hasSize(0)
        assertThat(statements).hasSize(1)

        val command = statements[0]
        assertThat(command).isInstanceOf(Pipeline::class.java)
        val pipeline = command as Pipeline

        assertThat(pipeline.commands)
            .containsExactly(
                Cat(listOf(Expr.BareWord("answer"))),
                Grep(listOf(Expr.Literal(Value.Number(42.0))))
            )

        assertThat(pipeline.redirections).isEqualTo(listOf(Redirection(GREATER, Expr.BareWord("result"))))
    }

    @Nested
    inner class MultiCommands {

        @Test
        fun `parses a simple multi command`() {
            val parser = Parser("cd topics; ls")

            val statements = parser.parse()
            assertThat(parser.errors).hasSize(0)
            assertThat(statements).hasSize(2)

            val firstCommand = statements[0]
            assertThat(firstCommand).isInstanceOf(Pipeline::class.java)
            require(firstCommand is Pipeline)
            assertThat(firstCommand.commands)
                .containsExactly(ShellCommand(Token(BAREWORD, "cd", 1, 2), listOf(Expr.BareWord("topics"))))

            val secondCommand = statements[1]
            assertThat(secondCommand).isInstanceOf(Pipeline::class.java)
            require(secondCommand is Pipeline)
            assertThat(secondCommand.commands)
                .containsExactly(ShellCommand(Token(BAREWORD, "ls", 1, 13), listOf()))
        }

        @Test
        fun `parses a simple multi command separated with newlines`() {
            val parser = Parser("cd topics\nls")

            val statements = parser.parse()
            assertThat(parser.errors).hasSize(0)
            assertThat(statements).hasSize(2)

            val firstCommand = statements[0]
            assertThat(firstCommand).isInstanceOf(Pipeline::class.java)
            require(firstCommand is Pipeline)
            assertThat(firstCommand.commands)
                .containsExactly(ShellCommand(Token(BAREWORD, "cd", 1, 2), listOf(Expr.BareWord("topics"))))

            val secondCommand = statements[1]
            assertThat(secondCommand).isInstanceOf(Pipeline::class.java)
            require(secondCommand is Pipeline)
            assertThat(secondCommand.commands)
            assertThat(secondCommand.commands)
                .containsExactly(ShellCommand(Token(BAREWORD, "ls", 2, 2), listOf()))
        }
    }

    @Test
    fun `parses a variable`() {
        val parser = Parser("cat \$foo")

        val statements = parser.parse()
        assertThat(parser.errors).hasSize(0)
        assertThat(statements).hasSize(1)

        val command = extractCommand(statements)
        assertThat(command).isEqualTo(Cat(listOf(Expr.Variable(Token(BAREWORD, "foo", 1, 8)))))
    }

    @Test
    fun `reports redirection errors correctly`() {
        val parser = Parser("cat word > cat")

        parser.parse()
        assertThat(parser.errors)
            .hasSize(1)
            .extracting("message")
            .containsExactly("expected start of expression")
    }

    @Test
    fun `parses enrichment blocks correctly`() {
        val parser = Parser("cat books | enrich { book -> http | cut .title }")

        val statements = parser.parse()
        assertThat(parser.errors)
            .hasSize(0)
        assertThat(statements).hasSize(1)

        assertThat(statements[0]).isInstanceOf(Pipeline::class.java)
        val pipeline = statements[0] as Pipeline

        assertThat(pipeline.commands).hasSize(2)

        val cat = pipeline.commands[0]

        assertThat(cat).isEqualTo(Cat(listOf(Expr.BareWord("books"))))

        val enrichCommand = pipeline.commands[1]
        assertThat(enrichCommand).isInstanceOf(Enrich::class.java)

        val enrich = enrichCommand as Enrich

        assertThat(enrich.expressionBlock)
            .isInstanceOf(Expr.Block::class.java)
            .extracting("argument", "pipeline")
            .containsExactly(
                Token(BAREWORD, "book", 1, 25),
                Pipeline(
                    listOf(
                        ShellCommand(Token(BAREWORD, "http", 1, 32), listOf()),
                        Cut(listOf(Expr.BareWord(".title")))
                    )
                )
            )
    }

    @Nested
    inner class Strings {
        @Test
        fun `parsers interpolated strings`() {
            val parser = Parser("cat \"books_#{\$foo}\"")

            val statements = parser.parse()
            assertThat(parser.errors)
                .hasSize(0)
            assertThat(statements).hasSize(1)

            assertThat(statements[0]).isInstanceOf(Pipeline::class.java)
            val pipeline = statements[0] as Pipeline

            assertThat(pipeline.commands).hasSize(1)

            val cat = pipeline.commands[0]

            assertThat(cat).isEqualTo(
                Cat(
                    listOf(
                        Expr.Binary(
                            Expr.Literal(Value.String("books_")),
                            PLUS,
                            Expr.Variable(Token(BAREWORD, "foo", 1, 16))
                        )
                    )
                )
            )
        }
    }

    @Nested
    inner class Conditionals {

        @Test
        fun `parses simple conditions`() {
            val parser = Parser("grep [.text == \"answer\"]")

            val statements = parser.parse()
            assertThat(parser.errors).hasSize(0)
            assertThat(statements).hasSize(1)

            val command = extractCommand(statements)
            assertThat(command).isEqualTo(
                Grep(
                    listOf(
                        Expr.Binary(
                            Expr.BareWord(".text"),
                            EQUAL_EQUAL,
                            Expr.Literal(Value.String("answer"))
                        )
                    )
                )
            )
        }

        @Test
        fun `parses chained conditions`() {
            val parser = Parser("grep [.title == \"Station eleven\" && .wordCount > 42]")

            val statements = parser.parse()
            assertThat(parser.errors).hasSize(0)
            assertThat(statements).hasSize(1)

            val command = extractCommand(statements)
            assertThat(command).isEqualTo(
                Grep(
                    listOf(
                        Expr.Binary(
                            Expr.Binary(
                                Expr.BareWord(".title"),
                                EQUAL_EQUAL,
                                Expr.Literal(Value.String("Station eleven"))
                            ),
                            AND,
                            Expr.Binary(
                                Expr.BareWord(".wordCount"),
                                GREATER,
                                Expr.Literal(Value.Number(42.0))
                            )
                        )
                    )
                )
            )
        }

        @Test
        fun `parses grouped conditions`() {
            val parser =
                Parser("grep [ (.text == \"answer\" || .text == \"42\") && .wordCount != 42 ]")

            val statements = parser.parse()
            assertThat(parser.errors).hasSize(0)
            assertThat(statements).hasSize(1)

            val command = extractCommand(statements)
            assertThat(command).isEqualTo(
                Grep(
                    listOf(
                        Expr.Binary(
                            Expr.Grouping(
                                Expr.Binary(
                                    Expr.Binary(
                                        Expr.BareWord(".text"),
                                        EQUAL_EQUAL,
                                        Expr.Literal(Value.String("answer"))
                                    ),
                                    OR,
                                    Expr.Binary(
                                        Expr.BareWord(".text"),
                                        EQUAL_EQUAL,
                                        Expr.Literal(Value.String("42"))
                                    )
                                )
                            ),
                            AND,
                            Expr.Binary(
                                Expr.BareWord(".wordCount"),
                                BANG_EQUAL,
                                Expr.Literal(Value.Number(42.0))
                            )
                        )
                    )
                )
            )
        }

        @Test
        fun `parses multiline conditions`() {
            val parser =
                Parser("grep [\n(.text == \"answer\" || .text == \"42\") &&\n.wordCount != 42\n]")

            val statements = parser.parse()
            assertThat(parser.errors).hasSize(0)
            assertThat(statements).hasSize(1)

            val command = extractCommand(statements)
            assertThat(command).isEqualTo(
                Grep(
                    listOf(
                        Expr.Binary(
                            Expr.Grouping(
                                Expr.Binary(
                                    Expr.Binary(
                                        Expr.BareWord(".text"),
                                        EQUAL_EQUAL,
                                        Expr.Literal(Value.String("answer"))
                                    ),
                                    OR,
                                    Expr.Binary(
                                        Expr.BareWord(".text"),
                                        EQUAL_EQUAL,
                                        Expr.Literal(Value.String("42"))
                                    )
                                )
                            ),
                            AND,
                            Expr.Binary(
                                Expr.BareWord(".wordCount"),
                                BANG_EQUAL,
                                Expr.Literal(Value.Number(42.0))
                            )
                        )
                    )
                )
            )
        }
    }
}
