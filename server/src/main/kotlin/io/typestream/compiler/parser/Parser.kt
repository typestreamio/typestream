package io.typestream.compiler.parser

import io.typestream.compiler.ast.Cat
import io.typestream.compiler.ast.Command
import io.typestream.compiler.ast.Cut
import io.typestream.compiler.ast.DataCommand
import io.typestream.compiler.ast.Echo
import io.typestream.compiler.ast.Enrich
import io.typestream.compiler.ast.Expr
import io.typestream.compiler.ast.Grep
import io.typestream.compiler.ast.Incomplete
import io.typestream.compiler.ast.Join
import io.typestream.compiler.ast.Pipeline
import io.typestream.compiler.ast.Redirection
import io.typestream.compiler.ast.ShellCommand
import io.typestream.compiler.ast.Statement
import io.typestream.compiler.ast.VarDeclaration
import io.typestream.compiler.ast.Wc
import io.typestream.compiler.lexer.CursorPosition
import io.typestream.compiler.lexer.ModalLexer
import io.typestream.compiler.lexer.SourceScanner
import io.typestream.compiler.lexer.Token
import io.typestream.compiler.lexer.TokenType
import io.typestream.compiler.lexer.TokenType.*
import io.typestream.compiler.types.Value


class Parser(source: String, cursor: CursorPosition? = null) {
    val errors = mutableListOf<ParseError>()

    private val lexer = ModalLexer(SourceScanner(source, cursor))

    private var previousToken = lexer.next()
    private var currentToken = previousToken

    fun parse() = buildList {
        while (!isAtEnd()) {
            when (peek().type) {
                LET -> add(varDeclaration())
                SEMICOLON, NEWLINE -> advance()
                INCOMPLETE -> synchronize()

                else -> add(pipeline())
            }
        }
    }

    private fun varDeclaration(): Statement {
        advance()

        val varName = consume(BAREWORD, "expected identifier after let")

        consume(EQUAL, "expected equal after var name")

        return VarDeclaration(varName, Expr.Assign(varName, expression()))
    }

    private fun expressionList() = buildList {
        while (check(DOLLAR, BAREWORD, STRING, STRING_START, NUMBER, LEFT_BRACKET)) {
            add(expression())
        }
    }

    private fun block(): Expr {
        consume(LEFT_CURLY, "expected block start")

        val argument = consume(BAREWORD, "expected argument")

        consume(LAMBDA_ARROW, "expected lambda arrow")

        val pipeline = pipeline()

        require(pipeline is Pipeline) { "expected pipeline block body" }

        consume(RIGHT_CURLY, "expected block end")

        return Expr.Block(argument, pipeline)
    }

    private fun expression(): Expr {
        return when (peek().type) {
            BAREWORD -> {
                val bareWord = consume(BAREWORD, "expected identifier")
                Expr.BareWord(bareWord.lexeme)
            }

            DOLLAR -> {
                advance()
                val variable = consume(BAREWORD, "expected identifier after dollar")
                Expr.Variable(variable)
            }

            STRING -> {
                val string = consume(STRING, "expected string")

                Expr.Literal(Value.String(string.lexeme))
            }

            STRING_START -> stringInterpolation()

            NUMBER -> {
                val number = consume(NUMBER, "expected number")

                Expr.Literal(Value.Number(number.lexeme.toDouble()))
            }

            LEFT_BRACKET -> {
                advance()
                val expr = equality()
                consume(RIGHT_BRACKET, "expected closing bracket")
                expr
            }

            else -> {
                error("expected start of expression", peek())
                incomplete()
            }
        }
    }

    private fun equality(): Expr {
        ignoreNewLines()

        var expr = comparison()

        while (check(AND, OR)) {
            val operator = advance()
            val right = comparison()
            expr = Expr.Binary(expr, operator.type, right)
            ignoreNewLines()
        }

        return expr
    }

    private fun comparison(): Expr {
        var expr = primary()

        while (check(LESS, LESS_EQUAL, EQUAL_EQUAL, GREATER, GREATER_EQUAL, BANG_EQUAL, ALMOST_EQUAL)) {
            val operator = advance()
            val right = primary()
            expr = Expr.Binary(expr, operator.type, right)
        }

        return expr
    }

    private fun primary(): Expr {
        ignoreNewLines()
        return when (peek().type) {
            BAREWORD -> {
                val bareWord = consume(BAREWORD, "expected identifier")
                Expr.BareWord(bareWord.lexeme)
            }

            DOLLAR -> {
                advance()
                val varName = consume(BAREWORD, "expected identifier after dollar")
                Expr.Variable(varName)
            }

            STRING -> {
                val string = consume(STRING, "expected string")

                Expr.Literal(Value.String(string.lexeme))
            }

            STRING_START -> stringInterpolation()
            STRING_LIT_PART -> {
                val string = consume(STRING_LIT_PART, "expected string literal part")
                Expr.Literal(Value.String(string.lexeme))
            }

            STRING_INTER_START -> {
                consume(STRING_INTER_START, "expected string interpolation start")
                val expr = expression()
                consume(STRING_INTER_END, "expected string interpolation end")
                expr
            }

            NUMBER -> {
                val number = consume(NUMBER, "expected number")

                Expr.Literal(Value.Number(number.lexeme.toDouble()))
            }

            LEFT_PAREN -> {
                advance()
                val expr = equality()
                consume(RIGHT_PAREN, "expected closing parenthesis")
                require(expr is Expr.Binary) { "expected binary expression" }
                Expr.Grouping(expr)
            }

            else -> {
                error("expected primary expression", peek())
                incomplete()
            }
        }
    }

    private fun incomplete() = Expr.Incomplete(peek())

    private fun stringInterpolation(): Expr {
        consume(STRING_START, "expected string start")

        var expr = primary()

        while (check(STRING_LIT_PART, STRING_INTER_START)) {
            val right = primary()
            expr = Expr.Binary(expr, PLUS, right)
        }

        consume(STRING_END, "expected string end")

        return expr
    }

    private fun pipeline(): Statement {
        val commands = mutableListOf<Command>()
        commands.add(command())
        while (check(PIPE)) {
            advance()
            commands.add(command())
        }
        return Pipeline(commands, redirectionList())
    }

    private fun command() = if (peek().type == BAREWORD) shellCommand() else dataCommand()

    private fun shellCommand() = ShellCommand(advance(), expressionList())

    private fun dataCommand(): DataCommand {
        return when (peek().type) {
            CAT -> {
                consume(CAT, "cat expected")
                Cat(expressionList())
            }

            CUT -> {
                consume(CUT, "cut expected")
                Cut(expressionList())
            }

            ECHO -> {
                consume(ECHO, "echo expected")
                Echo(expressionList())
            }

            ENRICH -> {
                consume(ENRICH, "enrich expected")
                Enrich(listOf(block()))
            }

            GREP -> {
                consume(GREP, "grep expected")
                Grep(expressionList())
            }

            JOIN -> {
                consume(JOIN, "join expected")
                Join(expressionList())
            }

            WC -> {
                consume(WC, "wc expected")
                Wc(expressionList())
            }

            else -> {
                error("expected data operator after pipe", peek())
                Incomplete(peek())
            }
        }
    }

    private fun redirectionList(): List<Redirection> {
        val redirections = mutableListOf<Redirection>()

        while (check(GREATER, GREATER_GREATER)) {
            redirections.add(Redirection(advance().type, expression()))
        }

        return redirections
    }

    private fun consume(type: TokenType, message: String): Token {
        if (check(type)) {
            return advance()
        }
        val token = peek()
        error(message, token)
        return token.copy(type = INCOMPLETE)
    }

    private fun check(vararg tokenTypes: TokenType) = !isAtEnd() && tokenTypes.any { peek().type == it }

    private fun ignoreNewLines() {
        while (!isAtEnd() && peek().type == NEWLINE) {
            advance()
        }
    }

    private fun advance(): Token {
        val previous = currentToken
        currentToken = lexer.next()
        previousToken = previous
        return previous
    }

    private fun isAtEnd() = currentToken.type === EOF

    private fun peek() = currentToken

    private fun error(message: String, token: Token) {
        errors.add(ParseError(message, token.line, token.column))
    }

    private fun synchronize() {
        advance()
        while (!isAtEnd()) {
            if (previousToken.type == SEMICOLON) return
            when (peek().type) {
                CAT, CUT, ECHO, ENRICH, GREP, JOIN, LET, WC -> return
                else -> {}
            }
            advance()
        }
    }
}
