package io.typestream.compiler.shellcommand

import io.typestream.compiler.ast.ShellCommand
import io.typestream.compiler.types.DataStream
import io.typestream.compiler.types.schema.Schema
import io.typestream.compiler.types.schema.fromJSON
import io.typestream.compiler.vm.Session
import io.typestream.http.HttpClient
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject

private val commands: Map<String, (session: Session, args: List<String>) -> ShellCommandOutput> = mapOf(
    "cd" to ::cd,
    "env" to ::env,
    "file" to ::file,
    "http" to ::http,
    "kill" to ::kill,
    "ls" to ::ls,
    "openai-complete" to ::openaiComplete,
    "ps" to ::ps,
    "stat" to ::stat,
)

fun ShellCommand.Companion.find(command: String) = commands[command]

fun ShellCommand.Companion.names() = commands.keys

private fun cd(session: Session, args: List<String>): ShellCommandOutput {
    val dir = args.firstOrNull()
    if (dir == null) {
        session.env.pwd = "/"
        return ShellCommandOutput.empty
    }

    val newPath = session.fileSystem.expandPath(dir, session.env.pwd)
        ?: return ShellCommandOutput.withError("cd: cannot cd into $dir: no such file or directory")

    return if (session.fileSystem.isDirectory(newPath)) {
        session.env.pwd = newPath
        ShellCommandOutput.withOutput(DataStream("/bin/cd", Schema.String(newPath)))
    } else {
        ShellCommandOutput.withError("cd: cannot cd into $dir: not a directory")
    }
}

private fun env(session: Session, args: List<String>) = if (args.isNotEmpty()) {
    ShellCommandOutput.withError("Usage: env")
} else {
    ShellCommandOutput.withOutput((session.env.toList() + session.env.toList()).sortedBy { it.first }.map {
        DataStream("/bin/env", Schema.String("${it.first}=${it.second}"))
    })
}

private fun file(session: Session, args: List<String>) = if (args.size != 1) {
    ShellCommandOutput.withError("Usage: file <path>")
} else {
    ShellCommandOutput.withOutput(
        DataStream("/bin/file", Schema.String(session.fileSystem.file(args[0], session.env.pwd)))
    )
}

private fun kill(session: Session, args: List<String>): ShellCommandOutput {
    if (args.size != 1) {
        return ShellCommandOutput.withError("Usage: kill <program id>")
    }

    session.scheduler.kill(args.first())

    return ShellCommandOutput.withError("kill: ${args.joinToString(" ")}")
}

private fun http(
    @Suppress("UNUSED_PARAMETER") session: Session,
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
    @Suppress("UNUSED_PARAMETER") session: Session,
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

private fun ls(session: Session, args: List<String>): ShellCommandOutput {
    val files = if (args.size < 2) {
        fileNames(session, args.getOrNull(0))
    } else {
        args.flatMap { fileNames(session, it) }
    }

    return ShellCommandOutput.withOutput(files.map { DataStream("/bin/ls", Schema.String(it)) })
}

private fun fileNames(session: Session, path: String?) =
    session.fileSystem.ls("${session.env.pwd}${if (path == null) "" else "/$path"}").sorted()

private fun stat(session: Session, args: List<String>) = if (args.size != 1) {
    ShellCommandOutput.withError("Usage: stat <path>")
} else {
    ShellCommandOutput.withOutput(
        DataStream(
            "/bin/stat", Schema.String(session.fileSystem.stat(args[0], session.env.pwd))
        )
    )
}

private fun ps(session: Session, args: List<String>) = if (args.isNotEmpty()) {
    ShellCommandOutput.withError("Usage: ps")
} else {
    ShellCommandOutput.withOutput(session.scheduler.ps().map { DataStream("/bin/ps", Schema.String(it)) })
}
