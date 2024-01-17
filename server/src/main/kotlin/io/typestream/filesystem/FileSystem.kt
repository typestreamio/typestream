package io.typestream.filesystem

import io.github.oshai.kotlinlogging.KotlinLogging
import io.typestream.compiler.ast.Cat
import io.typestream.compiler.ast.Command
import io.typestream.compiler.ast.Cut
import io.typestream.compiler.ast.DataCommand
import io.typestream.compiler.ast.Each
import io.typestream.compiler.ast.Echo
import io.typestream.compiler.ast.Enrich
import io.typestream.compiler.ast.Grep
import io.typestream.compiler.ast.Join
import io.typestream.compiler.ast.Pipeline
import io.typestream.compiler.ast.Wc
import io.typestream.compiler.types.Encoding
import io.typestream.config.SourcesConfig
import io.typestream.filesystem.catalog.Catalog
import io.typestream.filesystem.kafka.KafkaClusterDirectory
import io.typestream.filesystem.kafka.Topic
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import java.io.Closeable


class FileSystem(val sourcesConfig: SourcesConfig, private val dispatcher: CoroutineDispatcher) : Closeable {
    private val logger = KotlinLogging.logger {}

    private val kafkaDir = Directory("kafka")
    private val devDir = Directory("dev")
    private val root = Directory("/")
    private val catalog = Catalog(sourcesConfig, dispatcher)

    private val jobs = mutableListOf<Job>()

    companion object {
        const val KAFKA_CLUSTERS_PREFIX = "/dev/kafka"
    }

    init {
        sourcesConfig.kafka.forEach { (name, config) ->
            logger.info { "starting filesystem for kafka cluster: $name" }
            kafkaDir.add(KafkaClusterDirectory(name, config, dispatcher))
        }
        devDir.add(kafkaDir)
        root.add(devDir)
    }

    fun ls(path: String): List<String> {
        val children = if (path == "/") root.children() else (root.findInode(path)?.children() ?: setOf())
        return children.map { it.name }
    }

    suspend fun watch() = supervisorScope {
        jobs.add(launch(dispatcher) { root.watch() })
        jobs.add(launch(dispatcher) { catalog.watch() })
    }

    fun refresh() {
        root.refresh()
        catalog.refresh()
    }

    fun expandPath(path: String, pwd: String): String? {
        return when (path) {
            ".." -> {
                val paths = pwd.split("/")
                paths.subList(0, paths.lastIndex).joinToString("/").ifEmpty { "/" }
            }

            "." -> pwd
            "", "/" -> "/"
            else -> {
                val targetPath = if (path.startsWith("/")) path
                else if (pwd == "/") {
                    if (path.startsWith("/")) path else "/$path"
                } else {
                    "$pwd/$path"
                }
                if (root.findInode(targetPath) !== null) {
                    targetPath.removeSuffix("/")
                } else {
                    null
                }
            }
        }
    }

    fun stat(path: String) = root.findInode(path)?.stat() ?: error("cannot find $path")

    fun file(path: String, pwd: String): String {
        val targetPath = if (path.startsWith("/")) path else "$pwd/$path"
        val targetNode = root.findInode(targetPath)

        requireNotNull(targetNode) { "cannot find $targetPath" }

        return when (targetNode) {
            is Topic -> catalog[targetNode.path()]?.dataStream?.printTypes()
                ?: error("cannot find schema for $targetPath")

            is Directory -> "directory"
            else -> "non stream" //TODO cover other cases
        }
    }

    fun isDirectory(path: String): Boolean {
        val targetNode = root.findInode(path) ?: error("cannot find $path")

        return targetNode is Directory
    }

    fun findDataStream(path: String) = catalog[path]?.dataStream

    private fun findEncoding(path: String) = catalog[path]?.encoding

    fun inferEncoding(command: Command): Encoding {
        if (command !is DataCommand) {
            return Encoding.JSON
        }

        return when (command) {
            is Cat, is Grep -> {
                require(command.dataStreams.isNotEmpty()) { "cannot infer encoding for $command: unresolved data streams" }

                findEncoding(command.dataStreams.first().path)
                    ?: error("cannot infer encoding for $command: not found in catalog")
            }

            is Echo, is Join, is Wc -> Encoding.JSON
            else -> error("cannot infer encoding: $command not supported")
        }
    }

    fun inferEncoding(pipeline: Pipeline): Encoding {
        var encoding = inferEncoding(pipeline.commands.first())

        for (i in 1 until pipeline.commands.size) {
            when (pipeline.commands[i]) {
                is Grep, is Enrich, is Each -> {}
                is Cut, is Join, is Wc -> encoding += Encoding.JSON
                else -> error("cannot infer encoding for $pipeline: ${pipeline.commands[i]} not supported")
            }
        }

        return encoding
    }

    override fun close() {
        jobs.forEach(Job::cancel)
    }

    fun completePath(incompletePath: String, pwd: String) = buildList {
        val isAbsolute = incompletePath.startsWith("/")
        val targetPath = if (incompletePath.startsWith("/")) {
            incompletePath
        } else {
            if (pwd == "/") "/$incompletePath" else "$pwd/$incompletePath"
        }

        val isSubPath = !isAbsolute && incompletePath.contains("/")

        val parts = targetPath.split("/").dropLast(1)

        val targetPathBase = parts.joinToString("/")

        val children = if (targetPathBase == "/") root.children() else root.findInode(targetPathBase)?.children()

        val pwdPrefix = if (pwd == "/") "/" else "$pwd/"

        children?.forEach {
            val currentNodePath = it.path()
            if (currentNodePath.startsWith(targetPath)) {
                var suggestion = if (isAbsolute) {
                    currentNodePath
                } else if (isSubPath) {
                    currentNodePath.removePrefix(pwdPrefix)
                } else {
                    it.name
                }

                if (it is Directory) {
                    suggestion += "/"
                }

                add(suggestion)
            }
        }
    }

}
