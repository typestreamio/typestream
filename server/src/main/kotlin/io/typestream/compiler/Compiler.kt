package io.typestream.compiler

import io.typestream.compiler.ast.DataCommand
import io.typestream.compiler.ast.Expr
import io.typestream.compiler.ast.Incomplete
import io.typestream.compiler.ast.Pipeline
import io.typestream.compiler.ast.ShellCommand
import io.typestream.compiler.ast.Statement
import io.typestream.compiler.ast.VarDeclaration
import io.typestream.compiler.lexer.CursorPosition
import io.typestream.compiler.lexer.TokenType
import io.typestream.compiler.node.Node
import io.typestream.compiler.parser.Parser
import io.typestream.compiler.shellcommand.names
import io.typestream.compiler.types.DataStream
import io.typestream.compiler.types.Encoding
import io.typestream.compiler.vm.CompilerResult
import io.typestream.compiler.vm.Session
import io.typestream.filesystem.FileSystem
import io.typestream.graph.Graph
import java.util.UUID

class Compiler(private val session: Session) : Statement.Visitor<Unit> {
    private val root: Graph<Node> = Graph(Node.NoOp("root"))
    private var currentNode: Graph<Node> = root
    private val errors = mutableListOf<String>()

    fun compile(source: String): CompilerResult {
        val parser = Parser(source)
        val statements = parser.parse()
        if (parser.errors.isNotEmpty()) {
            parser.errors.forEach { errors.add(it.message.toString()) }
        }

        return compile(statements)
    }

    fun compile(statements: List<Statement>): CompilerResult {
        val id = "typestream-app-${UUID.randomUUID()}"
        val program = Program(id, root)

        if (errors.isNotEmpty()) {
            return CompilerResult(program, errors)
        }

        val interpreter = Interpreter(session)

        statements.forEach { it.accept(interpreter) }

        if (interpreter.errors.isNotEmpty()) {
            errors.addAll(interpreter.errors)
            return CompilerResult(program, errors)
        }

        statements.forEach {
            it.accept(this)
            currentNode = root
        }

        if (!program.hasRedirections() && program.hasStreamSources()) {
            val sinkNode: Graph<Node> = Graph(
                Node.Sink(
                    "$id-stdout", DataStream.fromString(
                        "${FileSystem.KAFKA_CLUSTERS_PREFIX}/${program.runtime().name}/topics/$id-stdout", ""
                    ), Encoding.JSON
                )
            )
            for (leaf in root.findLeaves()) {
                leaf.addChild(sinkNode)
            }
        }

        return CompilerResult(program, errors)
    }

    override fun visitVarDeclaration(varDeclaration: VarDeclaration) {}

    override fun visitShellCommand(shellCommand: ShellCommand) {
        val node: Graph<Node> = Graph(Node.ShellSource(shellCommand.toString(), shellCommand.dataStreams))

        currentNode.addChild(node)
        currentNode = node
    }

    override fun visitDataCommand(dataCommand: DataCommand) {
        val node = dataCommand.resolve()

        currentNode.addChild(node)
        currentNode = node
    }

    override fun visitPipeline(pipeline: Pipeline) {
        pipeline.commands.forEach { it.accept(this) }

        val lastCommand = pipeline.commands.last()
        val encoding = pipeline.encoding

        for (redirection in pipeline.redirections) {
            val dataStream = redirection.dataStream
            requireNotNull(dataStream) { "cannot add redirection node: $redirection, unresolved data stream" }
            requireNotNull(encoding) { "cannot add redirection node: $redirection, unresolved encoding" }

            currentNode.addChild(Graph(Node.Sink(lastCommand.toString(), dataStream, encoding)))
        }
    }

    fun complete(line: String, cursor: CursorPosition) = buildList {
        val parser = Parser(line, cursor)
        val statements = parser.parse()

        statements.forEach { statement ->
            when (statement) {
                is Pipeline -> {
                    when (val lastCommand = statement.commands.last()) {
                        is ShellCommand ->
                            if (lastCommand.expressions.isEmpty()) {
                                (TokenType.keywords.keys + ShellCommand.names()).filter { it.startsWith(lastCommand.token.lexeme) }
                                    .forEach(::add)
                            } else {
                                when (val lastExpr = lastCommand.expressions.lastOrNull()) {
                                    is Expr.BareWord -> session.fileSystem.completePath(
                                        lastExpr.value,
                                        session.env.pwd
                                    ).forEach(::add)

                                    else -> session.fileSystem.completePath(
                                        "/",
                                        session.env.pwd
                                    ).forEach(::add)
                                }
                            }

                        is Incomplete ->
                            TokenType.keywords.keys.filter { it.startsWith(lastCommand.token.lexeme) }
                                .forEach(::add)

                        is DataCommand -> {
                            when (val lastExpr = lastCommand.expressions.last()) {
                                is Expr.BareWord -> session.fileSystem.completePath(
                                    lastExpr.value,
                                    session.env.pwd
                                ).forEach(::add)

                                else -> {}
                            }
                        }
                    }
                }

                is ShellCommand -> ShellCommand.names().filter { it.startsWith(statement.token.lexeme) }.forEach(::add)

                else -> {}
            }
        }
    }.sorted()
}
