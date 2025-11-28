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
    val nodesById = graphProto.nodesList.associateBy { it.id }
    val adjList = mutableMapOf<String, MutableList<String>>()
    graphProto.edgesList.forEach { edge ->
      adjList.getOrPut(edge.fromId) { mutableListOf() }.add(edge.toId)
    }
    val sources = nodesById.keys - adjList.values.flatten().toSet()
    validateAcyclic(nodesById.keys, adjList)
    val rootGraphs = sources.map { buildGraph(it, nodesById, adjList) }
    val programGraph = if (rootGraphs.size == 1) rootGraphs.first() else error("Multi-source not supported yet")
    val root: Graph<Node> = Graph(Node.NoOp("root"))
    root.addChild(programGraph)
    Infer.infer(root)
    val programId = UUID.randomUUID().toString()
    return Program(programId, root)
  }

  private fun buildGraph(id: String, nodesById: Map<String, Job.PipelineNode>, adjList: MutableMap<String, MutableList<String>>): Graph<Node> {
    val protoNode = nodesById[id] ?: error("Missing node $id")
    val node = mapPipelineNode(protoNode)
    val children = adjList[id]?.map { buildGraph(it, nodesById, adjList) }?.toSet() ?: emptySet()
    return Graph(node, children.toMutableSet())
  }

  private fun mapPipelineNode(proto: Job.PipelineNode): Node = when {
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
      Node.StreamSource(proto.id, ds, ss.encoding.toEncoding())
    }
    proto.hasEach() -> Node.Each(proto.id) { _ -> }
    proto.hasSink() -> {
      val s = proto.sink
      val path = s.output.path
      val out = findDataStreamOrError(path)
      Node.Sink(proto.id, out, s.encoding.toEncoding())
    }
    else -> error("Unknown node type: $proto")
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
