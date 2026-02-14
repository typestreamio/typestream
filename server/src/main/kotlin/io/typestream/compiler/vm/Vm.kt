package io.typestream.compiler.vm

import io.github.oshai.kotlinlogging.KotlinLogging
import io.typestream.compiler.Compiler
import io.typestream.compiler.RuntimeType.KAFKA
import io.typestream.compiler.RuntimeType.SHELL
import io.typestream.compiler.node.ExecutionContext
import io.typestream.compiler.node.Node
import io.typestream.compiler.node.NodeShellSource
import io.typestream.compiler.types.DataStream
import io.typestream.embedding.EmbeddingGeneratorService
import io.typestream.filesystem.FileSystem
import io.typestream.geoip.GeoIpService
import io.typestream.openai.OpenAiService
import io.typestream.textextractor.TextExtractorService
import io.typestream.graph.Graph
import io.typestream.scheduler.KafkaStreamsJob
import io.typestream.scheduler.Scheduler

class Vm(val fileSystem: FileSystem, val scheduler: Scheduler) {
    private val logger = KotlinLogging.logger {}
    private val geoIpService = GeoIpService()
    private val textExtractorService = TextExtractorService()
    private val embeddingGeneratorService = EmbeddingGeneratorService()
    private val openAiService = OpenAiService()

    private val executionContext = ExecutionContext(
        geoIpService = geoIpService,
        textExtractorService = textExtractorService,
        embeddingGeneratorService = embeddingGeneratorService,
        openAiService = openAiService
    )

    fun exec(source: String, env: Env) {
        val (program, errors) = Compiler(Session(fileSystem, scheduler, env)).compile(source)
        require(errors.isEmpty()) { errors.joinToString("\n") }

        val runtime = program.runtime()
        when (runtime.type) {
            KAFKA -> {
                val kafkaConfig = fileSystem.config.sources.kafka[runtime.name]
                    ?: error("cluster ${runtime.name} not found")

                logger.info { "starting kafka streams job for ${program.id}" }

                KafkaStreamsJob(program.id, program, kafkaConfig, geoIpService, textExtractorService, embeddingGeneratorService, openAiService).start()
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

                scheduler.schedule(KafkaStreamsJob(program.id, program, kafkaConfig, geoIpService, textExtractorService, embeddingGeneratorService, openAiService))
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
        require(shellNode.ref is NodeShellSource) { "expected shell node" }

        var dataStreams = shellNode.ref.data
        shellNode.walk { node ->
            dataStreams = node.ref.applyToShell(dataStreams, executionContext)
        }

        dataStreams
    }
}
