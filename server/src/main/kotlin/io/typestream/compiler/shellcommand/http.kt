package io.typestream.compiler.shellcommand

import io.typestream.compiler.types.DataStream
import io.typestream.compiler.types.schema.Schema
import io.typestream.compiler.types.schema.fromJSON
import io.typestream.compiler.vm.Session
import io.typestream.http.HttpClient

private val verbs = listOf("get", "post")

fun http(
    @Suppress("UNUSED_PARAMETER") session: Session,
    args: List<String>,
): ShellCommandOutput {
    if (args.isEmpty()) {
        return ShellCommandOutput.withError("Usage: http [verb] <url> [body]")
    }

    var verb = "get"
    val arguments = args.toMutableList()

    if (verbs.contains(args.first())) {
        verb = arguments.removeFirst()
    }

    val url = arguments.first()

    val output = when(verb) {
        "get" -> HttpClient.get(url)
        "post" -> {
            if (arguments.size < 2) {
                return ShellCommandOutput.withError("Usage: http post <url> <body>")
            }
            val body = arguments[1]
            HttpClient.post(url, body)
        }
        else -> return ShellCommandOutput.withError("$verb requests not supported")
    }

    return ShellCommandOutput.withOutput(DataStream("/bin/http", Schema.Struct.fromJSON(output)))
}
