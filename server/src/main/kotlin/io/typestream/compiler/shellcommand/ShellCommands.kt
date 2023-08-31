package io.typestream.compiler.shellcommand

import io.typestream.compiler.ast.ShellCommand
import io.typestream.compiler.types.DataStream
import io.typestream.compiler.types.schema.Schema
import io.typestream.compiler.types.schema.fromJSON
import io.typestream.compiler.vm.Environment
import io.typestream.http.HttpClient
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject

private val commands: Map<String, (environment: Environment, args: List<String>) -> ShellCommandOutput> = mapOf(
    "cd" to ::cd,
    "env" to ::env,
    "file" to ::file,
    "http" to ::http,
    "kill" to ::kill,
    "kill-fg" to ::killFg,
    "ls" to ::ls,
    "openai-complete" to ::openaiComplete,
    "ps" to ::ps,
    "stat" to ::stat,
)

fun ShellCommand.Companion.find(command: String) = commands[command]

fun ShellCommand.Companion.names() = commands.keys

private fun cd(environment: Environment, args: List<String>): ShellCommandOutput {
    val dir = args.firstOrNull()
    if (dir == null) {
        environment.session.pwd = "/"
        return ShellCommandOutput.empty
    }

    val newPath = environment.fileSystem.expandPath(dir, environment.session.pwd)
        ?: return ShellCommandOutput.withError("cd: cannot cd into $dir: no such file or directory")

    return if (environment.fileSystem.isDirectory(newPath)) {
        environment.session.pwd = newPath
        ShellCommandOutput.withOutput(DataStream("/bin/cd", Schema.String(newPath)))
    } else {
        ShellCommandOutput.withError("cd: cannot cd into $dir: not a directory")
    }
}

private fun env(environment: Environment, args: List<String>) = if (args.isNotEmpty()) {
    ShellCommandOutput.withError("Usage: env")
} else {
    ShellCommandOutput.withOutput((environment.session.toList() + environment.toList()).sortedBy { it.first }.map {
        DataStream("/bin/env", Schema.String("${it.first}=${it.second}"))
    })
}

private fun file(environment: Environment, args: List<String>) = if (args.size != 1) {
    ShellCommandOutput.withError("Usage: file <path>")
} else {
    ShellCommandOutput.withOutput(
        DataStream("/bin/file", Schema.String(environment.fileSystem.file(args[0], environment.session.pwd)))
    )
}

private fun kill(environment: Environment, args: List<String>): ShellCommandOutput {
    if (args.size != 1) {
        return ShellCommandOutput.withError("Usage: kill <program id>")
    }

    environment.scheduler.kill(args.first())

    return ShellCommandOutput.withError("kill: ${args.joinToString(" ")}")
}

private fun killFg(
    environment: Environment,
    @Suppress("UNUSED_PARAMETER") args: List<String>,
): ShellCommandOutput {
    environment.scheduler.killFg()

    return ShellCommandOutput.empty
}

private fun http(
    @Suppress("UNUSED_PARAMETER") environment: Environment,
    args: List<String>,
): ShellCommandOutput {
    if (args.isEmpty()) {
        return ShellCommandOutput.withError("Usage: http <url>")
    }

    val url = args.first()
    val output = HttpClient.get(url)

    return ShellCommandOutput.withOutput(DataStream("/bin/http", Schema.Struct.fromJSON(output)))
}

private fun openaiComplete(
    @Suppress("UNUSED_PARAMETER") environment: Environment,
    args: List<String>,
): ShellCommandOutput {
    if (args.isEmpty()) {
        return ShellCommandOutput.withError("Usage: openai-complete <sentence>")
    }

    val apiKey = System.getenv("OPENAI_API_KEY") ?: return ShellCommandOutput.withError("OPENAI_API_KEY not set")

    val headers = mutableMapOf<String, String>()
    headers["Authorization"] = "Bearer $apiKey"

    val prompt = args.joinToString(" ")

    val output = HttpClient.post(
        "https://api.openai.com/v1/engines/text-davinci-001/completions",
        "{\"prompt\": \"$prompt\", \"max_tokens\": 100}",
        headers
    )

    val jsonElement = Json.parseToJsonElement(output)

    val json = jsonElement.jsonObject["choices"]?.jsonArray?.first().toString()

    return ShellCommandOutput.withOutput(DataStream("/bin/openai-complete", Schema.Struct.fromJSON(json)))
}

private fun ls(environment: Environment, args: List<String>): ShellCommandOutput {
    val files = if (args.size < 2) {
        fileNames(environment, args.getOrNull(0))
    } else {
        args.flatMap { fileNames(environment, it) }
    }

    return ShellCommandOutput.withOutput(files.map { DataStream("/bin/ls", Schema.String(it)) })
}

private fun fileNames(environment: Environment, path: String?) =
    environment.fileSystem.ls("${environment.session.pwd}${if (path == null) "" else "/$path"}").sorted()

private fun stat(environment: Environment, args: List<String>) = if (args.size != 1) {
    ShellCommandOutput.withError("Usage: stat <path>")
} else {
    ShellCommandOutput.withOutput(
        DataStream(
            "/bin/stat", Schema.String(environment.fileSystem.stat(args[0], environment.session.pwd))
        )
    )
}

private fun ps(environment: Environment, args: List<String>) = if (args.isNotEmpty()) {
    ShellCommandOutput.withError("Usage: ps")
} else {
    ShellCommandOutput.withOutput(environment.scheduler.ps().map { it.toString() }
        .map { DataStream("/bin/ps", Schema.String(it)) })
}
