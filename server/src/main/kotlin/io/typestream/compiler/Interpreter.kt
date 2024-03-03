package io.typestream.compiler

import io.typestream.compiler.ast.Cut
import io.typestream.compiler.ast.DataCommand
import io.typestream.compiler.ast.Each
import io.typestream.compiler.ast.Enrich
import io.typestream.compiler.ast.Expr
import io.typestream.compiler.ast.Grep
import io.typestream.compiler.ast.Pipeline
import io.typestream.compiler.ast.ShellCommand
import io.typestream.compiler.ast.Statement
import io.typestream.compiler.ast.VarDeclaration
import io.typestream.compiler.ast.Wc
import io.typestream.compiler.lexer.TokenType.GREATER_GREATER
import io.typestream.compiler.shellcommand.find
import io.typestream.compiler.types.DataStream
import io.typestream.compiler.types.Value
import io.typestream.compiler.types.inferType
import io.typestream.compiler.types.schema.Schema
import io.typestream.compiler.types.schema.empty
import io.typestream.compiler.types.value.fromBinary
import io.typestream.compiler.vm.Session
import io.typestream.compiler.vm.Vm

class Interpreter(private val session: Session) : Statement.Visitor<Unit>, Expr.Visitor<Value> {
    val errors = mutableListOf<String>()

    override fun visitShellCommand(shellCommand: ShellCommand) {
        val command = ShellCommand.find(shellCommand.token.lexeme)

        if (command == null) {
            errors.add("typestream: ${shellCommand.token.lexeme} not found")
            return
        }

        shellCommand.expressions.forEach { expr ->
            when (val arg = evaluate(expr)) {
                is Value.String -> shellCommand.boundArgs.add(arg.value)
                is DataStream -> shellCommand.boundArgs.add(arg.path)
                else -> error("typestream: $shellCommand does not support $arg")
            }
        }

        val programResult = command(session, shellCommand.boundArgs)
        shellCommand.dataStreams.addAll(programResult.output)
        errors.addAll(programResult.errors)
    }

    override fun visitDataCommand(dataCommand: DataCommand) {
        dataCommand.expressions.forEach { expr ->
            when (val arg = evaluate(expr)) {
                is DataStream -> {
                    dataCommand.dataStreams.add(arg)
                    dataCommand.encoding = session.fileSystem.inferEncoding(dataCommand)
                }

                is Value.List -> TODO("cannot evaluate lists yet")
                is Value.Number -> dataCommand.boundArgs.add(arg.value.toString())
                is Value.Predicate -> {
                    require(dataCommand is Grep) { "typestream: $dataCommand does not support predicates" }
                    dataCommand.predicates.add(arg.value)
                }

                is Value.String -> {
                    when (val expandedValue = expandString(arg.value)) {
                        is DataStream -> {
                            dataCommand.dataStreams.add(expandedValue)
                            dataCommand.encoding = session.fileSystem.inferEncoding(dataCommand)
                        }

                        is Value.String -> dataCommand.boundArgs.add(expandedValue.value)
                        else -> error("expanded value not supported: $expandedValue")
                    }
                }

                is Value.FieldAccess -> dataCommand.boundArgs.add(arg.value)
                is Value.Block -> {
                    when (dataCommand) {
                        is Enrich -> dataCommand.block = arg
                        is Each -> dataCommand.block = arg
                        else -> error("typestream: $dataCommand does not support blocks")
                    }
                }
            }
        }
    }

    override fun visitPipeline(pipeline: Pipeline) {
        pipeline.commands.forEach { it.accept(this) }

        pipeline.encoding = session.fileSystem.inferEncoding(pipeline)

        adjustEncoding(pipeline)

        bindRedirections(pipeline)

        typeCheck(pipeline)
    }

    private fun adjustEncoding(pipeline: Pipeline) {
        var encoding = pipeline.encoding

        for (i in pipeline.commands.size - 1 downTo 1) {
            val currentEncoding = pipeline.commands[i].encoding
            if (currentEncoding == null) {
                pipeline.commands[i].encoding = encoding
            } else {
                encoding = currentEncoding
            }
        }
    }

    private fun bindRedirections(pipeline: Pipeline) {
        if (pipeline.redirections.isEmpty()) {
            return
        }

        val typeStream = inferType(pipeline.commands)
        pipeline.redirections.forEach { redirect ->
            when (val value = evaluate(redirect.word)) {
                is Value.String -> {
                    val targetPath = getTargetPath(value.value)

                    require(session.fileSystem.findDataStream(targetPath) == null) {
                        "cannot redirect to existing data stream"
                    }

                    redirect.dataStream = typeStream.copy(path = targetPath)
                }

                is DataStream -> {
                    require(redirect.type == GREATER_GREATER) { "can only append to existing data streams" }

                    // if it's already in the catalog we can't overwrite
                    // TODO if it's there, check schemas are compatible
                    redirect.dataStream = value
                }

                else -> errors.add("cannot redirect to $value")
            }
        }
    }

    private fun typeCheck(pipeline: Pipeline) {
        if (pipeline.commands.isEmpty()) {
            return
        }

        //we don't type check shell commands because we don't know their schema
        val firstCommand = pipeline.commands.first()
        if (firstCommand is ShellCommand) {
            return
        }

        pipeline.commands.forEachIndexed { index, command ->
            when (command) {
                is DataCommand -> {
                    val typeStream = inferType(pipeline.commands.slice(0..index))

                    when (command) {
                        is Grep -> errors.addAll(command.predicates.flatMap { it.typeCheck(typeStream) })
                        is Cut -> command.boundArgs.forEach { key -> checkField(typeStream, key) }
                        is Wc -> {
                            // TODO we're parsing options twice.
                            //  To do it once, we need to do it at the end of visitDataCommand
                            val (options, _) = command.parseOptions()
                            if (options.by.isNotBlank()) {
                                checkField(typeStream, options.by)
                            }
                        }

                        else -> {}
                    }
                }

                is ShellCommand -> {}
            }
        }
    }

    override fun visitVarDeclaration(varDeclaration: VarDeclaration) {
        session.env.defineVariable(varDeclaration.token.lexeme, evaluate(varDeclaration.expr))
    }

    override fun visitAssign(assign: Expr.Assign): Value {
        val value = evaluate(assign.value)

        session.env.defineVariable(assign.name.lexeme, value)

        return value
    }

    override fun visitBareWord(bareWord: Expr.BareWord): Value {
        if (bareWord.value.startsWith(".") && bareWord.value != ".." && bareWord.value != ".") {
            return Value.FieldAccess(bareWord.value.substringAfter("."))
        }
        return expandString(bareWord.value)
    }

    override fun visitLiteral(literal: Expr.Literal): Value = literal.value

    override fun visitBinary(binary: Expr.Binary): Value =
        Value.fromBinary(evaluate(binary.left), binary.operator, evaluate(binary.right))

    override fun visitGrouping(grouping: Expr.Grouping): Value = evaluate(grouping.expr)

    override fun visitBlock(block: Expr.Block): Value {
        // We want to bound args for the block to the block itself
        // but also don't want to execute any shell command

        session.env.defineVariable(block.argument.lexeme, DataStream("block", Schema.Struct.empty()))
        block.pipeline.commands.forEach {
            when (it) {
                is ShellCommand -> {}
                // TODO also avoid binding vars?
                // Probably a sign that we should have a better way to handle blocks
                // as we're also compiling them at runtime which seems pretty horrible
                else -> it.accept(this)
            }
        }

        return Value.Block { dataStream ->
            val localSession = session.clone()
            localSession.env.defineVariable(block.argument.lexeme, dataStream)

            val compiler = Compiler(localSession)
            val compilerResult = compiler.compile(listOf(block.pipeline.clone()))

            if (compilerResult.errors.isNotEmpty()) {
                compilerResult.errors.forEach { errors.add(it) }
            }

            val vm = Vm(localSession.fileSystem, localSession.scheduler)
            val result =
                vm.eval(compilerResult.program.graph).firstOrNull() ?: DataStream("block", Schema.Struct.empty())

            dataStream.merge(result)
        }
    }

    //TODO the way we're doing this is not good enough.
    //Unfortunately I'm not sure how to do lexing of dots unless we get rid of bare words.
    override fun visitVariable(variable: Expr.Variable): Value {
        val parts = variable.name.lexeme.split(".")

        val key = parts.first()

        if (parts.size > 1) {
            val dataStream = session.env.getVariable(key)
            require(dataStream is DataStream) { "cannot access field '$key' of non data stream" }

            return Value.String(dataStream.schema.selectOne(parts.drop(1).joinToString(".")).toString())
        }

        return session.env.getVariable(key)
    }

    private fun evaluate(expr: Expr) = expr.accept(this)

    private fun getTargetPath(value: String) = if (value.startsWith("/")) {
        value
    } else {
        session.fileSystem.expandPath(value, session.env.pwd) ?: value
    }

    //TODO the message has the incorrect path (as it's the resulting type)
    private fun checkField(typeStream: DataStream, key: String) {
        if (!typeStream.hasField(key)) {
            errors.add(
                """
                    cannot find field '$key' in ${typeStream.path}.
                    You can use 'file ${typeStream.path}' to check available fields""".trimIndent()
            )
        }
    }

    private fun expandString(value: String): Value {
        val targetPath = getTargetPath(value)

        return session.fileSystem.findDataStream(targetPath) ?: Value.String(value)
    }
}
