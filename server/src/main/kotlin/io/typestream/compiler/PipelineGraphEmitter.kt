package io.typestream.compiler

import io.typestream.compiler.ast.Cat
import io.typestream.compiler.ast.Cut
import io.typestream.compiler.ast.DataCommand
import io.typestream.compiler.ast.Each
import io.typestream.compiler.ast.Echo
import io.typestream.compiler.ast.Enrich
import io.typestream.compiler.ast.Grep
import io.typestream.compiler.ast.Join
import io.typestream.compiler.ast.Pipeline
import io.typestream.compiler.ast.Predicate
import io.typestream.compiler.ast.ShellCommand
import io.typestream.compiler.ast.Statement
import io.typestream.compiler.ast.VarDeclaration
import io.typestream.compiler.ast.Wc
import io.typestream.compiler.node.JoinType
import io.typestream.grpc.job_service.Job

/**
 * Walks the interpreted AST and emits a PipelineGraph proto.
 *
 * This is the bridge between the text DSL and the unified PipelineGraph
 * intermediate representation. After interpretation (binding, type-checking),
 * this visitor converts the AST into PipelineGraph proto which then flows
 * through GraphCompiler for final compilation.
 *
 * Returns null if the AST contains constructs that can't be represented
 * in PipelineGraph (e.g., block expressions in enrich/each).
 */
class PipelineGraphEmitter : Statement.Visitor<Unit> {
    private val nodes = mutableListOf<Job.PipelineNode>()
    private val edges = mutableListOf<Job.PipelineEdge>()
    private var nodeCounter = 0
    private var lastNodeId: String? = null
    private var unsupported = false

    fun emit(statements: List<Statement>): Job.PipelineGraph? {
        statements.forEach { it.accept(this) }
        if (unsupported) return null
        return Job.PipelineGraph.newBuilder()
            .addAllNodes(nodes)
            .addAllEdges(edges)
            .build()
    }

    private fun nextId(prefix: String): String = "$prefix-${nodeCounter++}"

    private fun addNode(node: Job.PipelineNode) {
        nodes.add(node)
        val prevId = lastNodeId
        if (prevId != null) {
            edges.add(
                Job.PipelineEdge.newBuilder()
                    .setFromId(prevId)
                    .setToId(node.id)
                    .build()
            )
        }
        lastNodeId = node.id
    }

    override fun visitPipeline(pipeline: Pipeline) {
        lastNodeId = null
        pipeline.commands.forEach { it.accept(this) }

        if (unsupported) return

        for (redirection in pipeline.redirections) {
            val dataStream = redirection.dataStream
                ?: error("cannot emit redirection: unresolved data stream")

            val sinkId = nextId("sink")
            val sinkNode = Job.PipelineNode.newBuilder()
                .setId(sinkId)
                .setSink(
                    Job.SinkNode.newBuilder()
                        .setOutput(Job.DataStreamProto.newBuilder().setPath(dataStream.path))
                )
                .build()
            addNode(sinkNode)
        }
    }

    override fun visitDataCommand(dataCommand: DataCommand) {
        when (dataCommand) {
            is Cat -> emitCat(dataCommand)
            is Grep -> emitGrep(dataCommand)
            is Cut -> emitCut(dataCommand)
            is Wc -> emitWc(dataCommand)
            is Join -> emitJoin(dataCommand)
            is Echo -> emitEcho(dataCommand)
            is Enrich -> {
                unsupported = true
            }
            is Each -> {
                unsupported = true
            }
            else -> {
                unsupported = true
            }
        }
    }

    override fun visitShellCommand(shellCommand: ShellCommand) {
        val shellId = nextId("shell")
        val dataProtos = shellCommand.dataStreams.map { ds ->
            Job.DataStreamProto.newBuilder().setPath(ds.path).build()
        }
        val node = Job.PipelineNode.newBuilder()
            .setId(shellId)
            .setShellSource(
                Job.ShellSourceNode.newBuilder().addAllData(dataProtos)
            )
            .build()
        addNode(node)
    }

    override fun visitVarDeclaration(varDeclaration: VarDeclaration) {
        // Variable declarations don't produce pipeline nodes
    }

    private fun emitCat(cat: Cat) {
        require(cat.dataStreams.isNotEmpty()) { "cannot emit cat: unbound data streams" }

        val sourceId = nextId("source")
        val node = Job.PipelineNode.newBuilder()
            .setId(sourceId)
            .setStreamSource(
                Job.StreamSourceNode.newBuilder()
                    .setDataStream(
                        Job.DataStreamProto.newBuilder().setPath(cat.dataStreams.first().path)
                    )
                    .setEncoding(cat.encoding.toProtoEncoding())
            )
            .build()
        addNode(node)
    }

    private fun emitGrep(grep: Grep) {
        // If grep has a data stream, it's a standalone command (grep pattern /path)
        // Emit the source first
        if (grep.dataStreams.isNotEmpty()) {
            val sourceId = nextId("source")
            val sourceNode = Job.PipelineNode.newBuilder()
                .setId(sourceId)
                .setStreamSource(
                    Job.StreamSourceNode.newBuilder()
                        .setDataStream(
                            Job.DataStreamProto.newBuilder().setPath(grep.dataStreams.first().path)
                        )
                        .setEncoding(grep.encoding.toProtoEncoding())
                )
                .build()
            addNode(sourceNode)
        }

        val (options, args) = grep.parseOptions()

        // Build the predicate expression string
        val predicateExpr = if (grep.predicates.isNotEmpty()) {
            var predicate = grep.predicates.reduce(Predicate::and)
            if (options.invertMatch) {
                predicate = predicate.not()
            }
            predicate.toExpr()
        } else {
            val pattern = args.firstOrNull() ?: ""
            var predicate = Predicate.matches(pattern)
            if (options.invertMatch) {
                predicate = predicate.not()
            }
            predicate.toExpr()
        }

        val filterId = nextId("filter")
        val filterNode = Job.PipelineNode.newBuilder()
            .setId(filterId)
            .setFilter(
                Job.FilterNode.newBuilder()
                    .setByKey(options.byKey)
                    .setPredicate(
                        Job.PredicateProto.newBuilder().setExpr(predicateExpr)
                    )
            )
            .build()
        addNode(filterNode)
    }

    private fun emitCut(cut: Cut) {
        val mapperExpr = "select " + cut.boundArgs.joinToString(" ") { ".$it" }

        val mapId = nextId("map")
        val node = Job.PipelineNode.newBuilder()
            .setId(mapId)
            .setMap(
                Job.MapNode.newBuilder().setMapperExpr(mapperExpr)
            )
            .build()
        addNode(node)
    }

    private fun emitWc(wc: Wc) {
        val (options, _) = wc.parseOptions()

        val keyMapperExpr = if (options.by.isBlank()) "" else ".${options.by}"

        val groupId = nextId("group")
        val groupNode = Job.PipelineNode.newBuilder()
            .setId(groupId)
            .setGroup(
                Job.GroupNode.newBuilder().setKeyMapperExpr(keyMapperExpr)
            )
            .build()
        addNode(groupNode)

        val countId = nextId("count")
        val countNode = Job.PipelineNode.newBuilder()
            .setId(countId)
            .setCount(Job.CountNode.newBuilder())
            .build()
        addNode(countNode)
    }

    private fun emitJoin(join: Join) {
        require(join.dataStreams.isNotEmpty()) { "cannot emit join: unbound data streams" }

        val joinId = nextId("join")
        val node = Job.PipelineNode.newBuilder()
            .setId(joinId)
            .setJoin(
                Job.JoinNode.newBuilder()
                    .setWith(
                        Job.DataStreamProto.newBuilder().setPath(join.dataStreams.first().path)
                    )
                    .setJoinType(
                        Job.JoinTypeProto.newBuilder()
                            .setByKey(true)
                            .setIsLookup(false)
                    )
            )
            .build()
        addNode(node)
    }

    private fun emitEcho(echo: Echo) {
        val dataProtos = mutableListOf<Job.DataStreamProto>()

        echo.dataStreams.forEach { ds ->
            dataProtos.add(Job.DataStreamProto.newBuilder().setPath(ds.path).build())
        }
        echo.boundArgs.forEach { arg ->
            dataProtos.add(Job.DataStreamProto.newBuilder().setPath("/bin/echo").build())
        }

        val shellId = nextId("shell")
        val node = Job.PipelineNode.newBuilder()
            .setId(shellId)
            .setShellSource(
                Job.ShellSourceNode.newBuilder().addAllData(dataProtos)
            )
            .build()
        addNode(node)
    }

    private fun Grep.parseOptions(): Pair<Grep.Options, List<String>> {
        return io.typestream.option.parseOptions<Grep.Options>(boundArgs)
    }
}

private fun io.typestream.compiler.types.Encoding?.toProtoEncoding(): Job.Encoding = when (this) {
    io.typestream.compiler.types.Encoding.STRING -> Job.Encoding.STRING
    io.typestream.compiler.types.Encoding.NUMBER -> Job.Encoding.NUMBER
    io.typestream.compiler.types.Encoding.JSON -> Job.Encoding.JSON
    io.typestream.compiler.types.Encoding.AVRO -> Job.Encoding.AVRO
    io.typestream.compiler.types.Encoding.PROTOBUF -> Job.Encoding.PROTOBUF
    null -> Job.Encoding.AVRO
}
