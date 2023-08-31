package io.typestream.compiler.types

import io.typestream.compiler.ast.Command
import io.typestream.compiler.ast.Cut
import io.typestream.compiler.ast.Grep
import io.typestream.compiler.types.datastream.join

fun inferType(commands: List<Command>): DataStream {
    val types: List<DataStream?> = commands.map(Command::inferType)

    var resultingType = types.first() ?: error("cannot infer type: no starting data stream")

    for (i in 1 until types.size) {
        val currentType = types[i]
        if (currentType == null) {
            resultingType = when (val currentCommand = commands[i]) {
                is Cut -> currentCommand.deriveDataStream(resultingType)
                is Grep -> resultingType
                else -> error("cannot infer type: $currentCommand not supported")
            }
        } else {
            resultingType = resultingType.join(currentType)
        }
    }

    return resultingType
}
