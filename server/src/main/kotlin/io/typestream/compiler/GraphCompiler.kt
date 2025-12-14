package io.typestream.compiler

import io.typestream.compiler.ast.Predicate
import io.typestream.compiler.node.*
import io.typestream.compiler.types.DataStream
import io.typestream.compiler.types.Encoding
import io.typestream.compiler.types.schema.Schema
import io.typestream.filesystem.FileSystem
import io.typestream.graph.Graph
import io.typestream.grpc.job_service.Job
import java.util.ArrayDeque
import java.util.UUID

class GraphCompiler(private val fileSystem: FileSystem) {

  private fun findDataStreamOrError(path: String): DataStream =
    fileSystem.findDataStream(path) ?: error("No DataStream for path: $path")

  fun compile(request: Job.CreateJobFromGraphRequest): Program {
    val graphProto = request.graph

    // Phase 1: Infer schemas and encodings for all nodes
    val (inferredSchemas, inferredEncodings) = inferNodeSchemasAndEncodings(graphProto)

    // Phase 2: Build graph using inferred schemas and encodings
    val nodesById = graphProto.nodesList.associateBy { it.id }
    val adjList = mutableMapOf<String, MutableList<String>>()
    graphProto.edgesList.forEach { edge ->
      adjList.getOrPut(edge.fromId) { mutableListOf() }.add(edge.toId)
    }
    val sources = nodesById.keys - adjList.values.flatten().toSet()
    validateAcyclic(nodesById.keys, adjList)
    val rootGraphs = sources.map { buildGraph(it, nodesById, adjList, inferredSchemas, inferredEncodings) }
    val programGraph = if (rootGraphs.size == 1) rootGraphs.first() else error("Multi-source not supported yet")
    val root: Graph<Node> = Graph(Node.NoOp("root"))
    root.addChild(programGraph)
    Infer.infer(root)  // Validation still works!
    val programId = UUID.randomUUID().toString()
    return Program(programId, root)
  }

  private fun buildGraph(
    id: String,
    nodesById: Map<String, Job.PipelineNode>,
    adjList: MutableMap<String, MutableList<String>>,
    inferredSchemas: Map<String, DataStream>,
    inferredEncodings: Map<String, Encoding>
  ): Graph<Node> {
    val protoNode = nodesById[id] ?: error("Missing node $id")
    val node = mapPipelineNode(protoNode, inferredSchemas, inferredEncodings)
    val children = adjList[id]?.map { buildGraph(it, nodesById, adjList, inferredSchemas, inferredEncodings) }?.toSet() ?: emptySet()
    return Graph(node, children.toMutableSet())
  }

  private fun mapPipelineNode(
    proto: Job.PipelineNode,
    inferredSchemas: Map<String, DataStream>,
    inferredEncodings: Map<String, Encoding>
  ): Node = when {
    proto.hasCount() -> Node.Count(proto.id)
    proto.hasFilter() -> {
      val f = proto.filter
      Node.Filter(proto.id, f.byKey, Predicate.matches(f.predicate.expr))
    }
    proto.hasGroup() -> {
      val stubSchema = Schema.Struct(listOf())
      val stubDs = DataStream("/stub/key", stubSchema)
      Node.Group(proto.id) { _ -> stubDs }
    }
    proto.hasJoin() -> {
      val j = proto.join
      val path = j.with.path
      val with = findDataStreamOrError(path)
      Node.Join(proto.id, with, JoinType(j.joinType.byKey, j.joinType.isLookup))
    }
    proto.hasMap() -> Node.Map(proto.id) { kv -> kv }
    proto.hasNoop() -> Node.NoOp(proto.id)
    proto.hasShellSource() -> {
      val data = proto.shellSource.dataList.map { dsProto ->
        findDataStreamOrError(dsProto.path)
      }
      Node.ShellSource(proto.id, data)
    }
    proto.hasStreamSource() -> {
      val ss = proto.streamSource
      val path = ss.dataStream.path
      val ds = findDataStreamOrError(path)
      // Query catalog for actual encoding (AVRO, STRING, etc.), don't trust proto
      val encoding = fileSystem.inferEncodingForPath(path)
      Node.StreamSource(proto.id, ds, encoding)
    }
    proto.hasEach() -> Node.Each(proto.id) { _ -> }
    proto.hasSink() -> {
      val s = proto.sink
      // Use inferred schema from input stream
      val out = inferredSchemas[proto.id]
        ?: error("No inferred schema for sink ${proto.id}")
      // Use inferred encoding from input stream (not proto, not catalog)
      val encoding = inferredEncodings[proto.id]
        ?: error("No inferred encoding for sink ${proto.id}")
      Node.Sink(proto.id, out, encoding)
    }
    else -> error("Unknown node type: $proto")
  }

  /**
   * Phase 1: Infer schemas and encodings for all nodes in the graph.
   * This creates two in-memory maps:
   * - node ID -> DataStream with inferred schema
   * - node ID -> Encoding (propagated from sources through the pipeline)
   * These are then used in Phase 2 (buildGraph) to construct nodes.
   */
  private fun inferNodeSchemasAndEncodings(graph: Job.PipelineGraph): Pair<Map<String, DataStream>, Map<String, Encoding>> {
    val schemas = mutableMapOf<String, DataStream>()
    val encodings = mutableMapOf<String, Encoding>()
    val nodesById = graph.nodesList.associateBy { it.id }
    val adjList = mutableMapOf<String, MutableList<String>>()

    graph.edgesList.forEach { edge ->
      adjList.getOrPut(edge.fromId) { mutableListOf() }.add(edge.toId)
    }

    val sources = nodesById.keys - adjList.values.flatten().toSet()

    sources.forEach { sourceId ->
      inferNodeType(sourceId, nodesById, adjList, null, null, schemas, encodings)
    }

    return schemas to encodings
  }

  /**
   * Recursively infer the output schema and encoding for a node and its children.
   * Uses TypeRules for all type transformations to ensure consistency.
   * Encodings are propagated from sources through the pipeline.
   */
  private fun inferNodeType(
    nodeId: String,
    nodesById: Map<String, Job.PipelineNode>,
    adjList: Map<String, List<String>>,
    input: DataStream?,
    inputEncoding: Encoding?,
    schemas: MutableMap<String, DataStream>,
    encodings: MutableMap<String, Encoding>
  ) {
    val proto = nodesById[nodeId] ?: error("Missing node $nodeId")

    val (output, outputEncoding) = when {
      proto.hasStreamSource() -> {
        val path = proto.streamSource.dataStream.path
        val ds = io.typestream.compiler.types.TypeRules.inferStreamSource(path, fileSystem)
        val enc = fileSystem.inferEncodingForPath(path)
        ds to enc
      }
      proto.hasFilter() -> {
        val out = io.typestream.compiler.types.TypeRules.inferFilter(input ?: error("filter $nodeId missing input"))
        out to (inputEncoding ?: Encoding.AVRO)
      }
      proto.hasMap() -> {
        val out = io.typestream.compiler.types.TypeRules.inferMap(input ?: error("map $nodeId missing input"))
        out to (inputEncoding ?: Encoding.AVRO)
      }
      proto.hasJoin() -> {
        val stream = input ?: error("join $nodeId missing input")
        val withPath = proto.join.with.path
        val withStream = io.typestream.compiler.types.TypeRules.inferStreamSource(withPath, fileSystem)
        val out = io.typestream.compiler.types.TypeRules.inferJoin(stream, withStream)
        out to (inputEncoding ?: Encoding.AVRO)
      }
      proto.hasGroup() -> {
        val out = io.typestream.compiler.types.TypeRules.inferGroup(input ?: error("group $nodeId missing input"))
        out to (inputEncoding ?: Encoding.AVRO)
      }
      proto.hasCount() -> {
        val out = io.typestream.compiler.types.TypeRules.inferCount(input ?: error("count $nodeId missing input"))
        out to (inputEncoding ?: Encoding.AVRO)
      }
      proto.hasEach() -> {
        val out = io.typestream.compiler.types.TypeRules.inferEach(input ?: error("each $nodeId missing input"))
        out to (inputEncoding ?: Encoding.AVRO)
      }
      proto.hasSink() -> {
        val sink = proto.sink
        val out = io.typestream.compiler.types.TypeRules.inferSink(
          input ?: error("sink $nodeId missing input"),
          sink.output.path
        )
        out to (inputEncoding ?: Encoding.AVRO)
      }
      proto.hasNoop() -> {
        val out = io.typestream.compiler.types.TypeRules.inferNoOp(input ?: error("noop $nodeId missing input"))
        out to (inputEncoding ?: Encoding.AVRO)
      }
      proto.hasShellSource() -> {
        val dataStreams = proto.shellSource.dataList.map { dsProto ->
          io.typestream.compiler.types.TypeRules.inferStreamSource(dsProto.path, fileSystem)
        }
        val out = io.typestream.compiler.types.TypeRules.inferShellSource(dataStreams)
        out to Encoding.JSON
      }
      else -> error("Unknown node type: $nodeId")
    }

    schemas[nodeId] = output
    encodings[nodeId] = outputEncoding

    // Recursively process children with propagated encoding
    adjList[nodeId]?.forEach { childId ->
      inferNodeType(childId, nodesById, adjList, output, outputEncoding, schemas, encodings)
    }
  }

  private fun validateAcyclic(nodes: Set<String>, adj: Map<String, List<String>>) {
    val indegree = nodes.associateWith { 0 }.toMutableMap()
    adj.values.flatten().forEach { indegree[it] = (indegree[it] ?: 0) + 1 }
    val queue = indegree.filter { it.value == 0 }.keys
    var processed = 0
    val q = ArrayDeque(queue)
    while (q.isNotEmpty()) {
      val u = q.removeFirst()
      processed++
      adj[u]?.forEach { v ->
        indegree[v] = indegree[v]!! - 1
        if (indegree[v] == 0) q.add(v)
      }
    }
    if (processed != nodes.size) error("Cycle detected")
  }
}

fun Job.Encoding.toEncoding() = when (this) {
  Job.Encoding.STRING -> Encoding.STRING
  Job.Encoding.NUMBER -> Encoding.NUMBER
  Job.Encoding.JSON -> Encoding.JSON
  Job.Encoding.AVRO -> Encoding.AVRO
  Job.Encoding.PROTOBUF -> Encoding.PROTOBUF
  else -> error("Unknown encoding")
}
