package io.typestream.compiler.vm

import io.github.oshai.kotlinlogging.KotlinLogging
import io.typestream.compiler.Compiler
import io.typestream.compiler.RuntimeType.KAFKA
import io.typestream.compiler.RuntimeType.SHELL
import io.typestream.compiler.node.KeyValue
import io.typestream.compiler.node.Node
import io.typestream.compiler.types.DataStream
import io.typestream.embedding.EmbeddingGeneratorExecution
import io.typestream.embedding.EmbeddingGeneratorService
import io.typestream.filesystem.FileSystem
import io.typestream.geoip.GeoIpExecution
import io.typestream.geoip.GeoIpService
import io.typestream.textextractor.TextExtractorExecution
import io.typestream.textextractor.TextExtractorService
import io.typestream.graph.Graph
import io.typestream.scheduler.KafkaStreamsJob
import io.typestream.scheduler.Scheduler

class Vm(val fileSystem: FileSystem, val scheduler: Scheduler) {
    private val logger = KotlinLogging.logger {}
    private val geoIpService = GeoIpService()
    private val textExtractorService = TextExtractorService()
    private val embeddingGeneratorService = EmbeddingGeneratorService()

    fun exec(source: String, env: Env) {
        val (program, errors) = Compiler(Session(fileSystem, scheduler, env)).compile(source)
        require(errors.isEmpty()) { errors.joinToString("\n") }

        val runtime = program.runtime()
        when (runtime.type) {
            KAFKA -> {
                val kafkaConfig = fileSystem.config.sources.kafka[runtime.name]
                    ?: error("cluster ${runtime.name} not found")

                logger.info { "starting kafka streams job for ${program.id}" }

                KafkaStreamsJob(program.id, program, kafkaConfig, geoIpService, textExtractorService, embeddingGeneratorService).start()
            }

            SHELL -> {
                val dataStreams = eval(program.graph)
                logger.info { dataStreams.joinToString("\n") { it.prettyPrint() } }
            }
        }
    }

    suspend fun run(source: String, session: Session): VmResult {
        val (program, errors) = Compiler(session).compile(source)
        if (errors.isNotEmpty()) {
            return VmResult(program, ProgramOutput("", errors.joinToString("\n")))
        }

        return runProgram(program, session)
    }

    suspend fun runProgram(program: io.typestream.compiler.Program, session: Session): VmResult {
        val runtime = program.runtime()
        if (runtime.type != SHELL && !program.hasMoreOutput()) {
            session.runningPrograms.add(program)
        }
        return when (runtime.type) {
            KAFKA -> {
                val kafkaConfig = fileSystem.config.sources.kafka[runtime.name]
                    ?: error("cluster ${runtime.name} not found")

                scheduler.schedule(KafkaStreamsJob(program.id, program, kafkaConfig, geoIpService, textExtractorService, embeddingGeneratorService))
                val stdOut = if (!program.hasMoreOutput()) "running ${program.id} in the background" else ""
                VmResult(program, ProgramOutput(stdOut, ""))
            }

            SHELL -> {
                val dataStreams = eval(program.graph)
                VmResult(
                    program, ProgramOutput(
                        dataStreams.joinToString("\n") { it.prettyPrint() }, ""
                    )
                )
            }
        }
    }

    fun eval(graph: Graph<Node>): List<DataStream> = graph.children.flatMap { shellNode ->
        require(shellNode.ref is Node.ShellSource) { "expected shell node" }

        var dataStreams = shellNode.ref.data
        shellNode.walk { node ->
            dataStreams = when (node.ref) {
                is Node.Count -> TODO("count node not implemented")
                is Node.Group -> TODO("group node not implemented")
                is Node.Filter -> dataStreams.filter { node.ref.predicate.matches(it) }
                is Node.Map -> dataStreams.map { node.ref.mapper(KeyValue(it, it)).value }
                is Node.Each -> {
                    dataStreams.forEach { node.ref.fn(KeyValue(it, it)) }
                    dataStreams
                }

                is Node.GeoIp -> GeoIpExecution.applyToShell(node.ref, dataStreams, geoIpService)
                is Node.TextExtractor -> TextExtractorExecution.applyToShell(node.ref, dataStreams, textExtractorService)
                is Node.EmbeddingGenerator -> EmbeddingGeneratorExecution.applyToShell(node.ref, dataStreams, embeddingGeneratorService)

                is Node.ShellSource -> dataStreams
                else -> error("unexpected node type: ${node.ref}")
            }
        }

        dataStreams
    }
}
