package io.typestream.compiler.shellcommand

import io.typestream.compiler.types.DataStream

data class ShellCommandOutput(val output: List<DataStream>, val errors: List<String>) {
    companion object {
        val empty = ShellCommandOutput(DataStream.devNull, listOf())
        fun withError(error: String) = ShellCommandOutput(DataStream.devNull, listOf(error))
        fun withOutput(output: List<DataStream>) = ShellCommandOutput(output, listOf())
        fun withOutput(output: DataStream) = withOutput(listOf(output))
    }
}
