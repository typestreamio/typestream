package io.typestream.compiler.shellcommand

import io.typestream.compiler.types.DataStream
import io.typestream.compiler.types.schema.Schema
import io.typestream.compiler.vm.Session
import io.typestream.option.Option
import io.typestream.option.parseOptions

data class HistoryOptions(
    @param:Option(
        names = ["-p", "--print-session"],
        description = "inverts matching pattern"
    ) val printSession: Boolean = false,
)

fun history(session: Session, args: List<String>): ShellCommandOutput {
    val (options, _) = parseOptions<HistoryOptions>(args)

    if (options.printSession) {
        return ShellCommandOutput.withOutput(session.env.history().map { command ->
            DataStream("/bin/history", Schema.String(command))
        })
    }

    return ShellCommandOutput.withOutput(session.env.history().mapIndexed { index, command ->
        DataStream("/bin/history", Schema.String("${index + 1} $command"))
    })
}
