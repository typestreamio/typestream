package io.typestream.compiler.types

import io.typestream.compiler.ast.Command
import io.typestream.compiler.ast.Cut
import io.typestream.compiler.ast.DataCommand
import io.typestream.compiler.ast.Echo
import io.typestream.compiler.ast.Enrich
import io.typestream.compiler.ast.ShellCommand
import io.typestream.compiler.types.datastream.join
import io.typestream.compiler.types.schema.Schema
import io.typestream.compiler.types.schema.empty

fun inferType(commands: List<Command>): DataStream {
    var resultingType: DataStream = when (val command = commands.firstOrNull()) {
        is ShellCommand -> DataStream("/bin/${command.token.lexeme}", Schema.Struct.empty())
        is Echo -> DataStream("/bin/echo", Schema.Struct.empty())
        is DataCommand -> command.dataStreams.reduce(DataStream::join)
        else -> error("cannot infer type: $commands not supported")
    }

    commands.forEach { command ->
        resultingType = when (command) {
            is Enrich -> resultingType.merge(inferType(command.expressionBlock.pipeline.commands))
            is Cut -> if (resultingType.schema == Schema.Struct.empty()) {
                resultingType.merge(
                    DataStream(
                        "/bin/cut",
                        Schema.Struct(command.boundArgs.map { Schema.Field(it, Schema.String.zeroValue) })
                    )
                )
            } else {
                resultingType.select(command.boundArgs)
            }

            else -> resultingType
        }
    }

    return resultingType
}
