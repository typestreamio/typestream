package io.typestream.compiler.vm

import io.typestream.compiler.Program
import io.typestream.compiler.RuntimeType.KAFKA
import io.typestream.compiler.RuntimeType.SHELL
import io.typestream.compiler.node.KeyValue
import io.typestream.compiler.node.Node
import io.typestream.compiler.types.DataStream
import io.typestream.filesystem.FileSystem
import io.typestream.graph.Graph
import io.typestream.scheduler.KafkaStreamsJob
import io.typestream.scheduler.Scheduler

class Vm(val fileSystem: FileSystem, val scheduler: Scheduler) {
    suspend fun run(program: Program): ProgramOutput {
        val runtime = program.runtime()
        return when (runtime.type) {
            KAFKA -> {
                val kafkaConfig = scheduler.sourcesConfig.kafkaClustersConfig.clusters[runtime.name]
                    ?: error("cluster ${runtime.name} not found")

                scheduler.schedule(KafkaStreamsJob(program, kafkaConfig))
                ProgramOutput("", "")
            }

            SHELL -> {
                val dataStreams = eval(program.graph)
                ProgramOutput(dataStreams.joinToString("\n") { it.prettyPrint() }, "")
            }
        }
    }

    fun eval(graph: Graph<Node>): List<DataStream> {
        val outputStreams = mutableListOf<DataStream>()
        graph.children.forEach { shellNode ->
            require(shellNode.ref is Node.ShellSource) { "expected shell node" }

            var dataStreams = shellNode.ref.data
            shellNode.walk { node ->
                dataStreams = when (node.ref) {
                    is Node.Count -> TODO("count node not implemented")
                    is Node.Group -> TODO("group node not implemented")
                    is Node.Filter -> dataStreams.filter { node.ref.predicate.matches(it) }
                    is Node.Map -> dataStreams.map { node.ref.mapper(KeyValue(it, it)).value }
                    is Node.ShellSource -> dataStreams
                    else -> error("unexpected node type: ${node.ref}")
                }
            }

            outputStreams.addAll(dataStreams)
        }

        return outputStreams
    }
}
