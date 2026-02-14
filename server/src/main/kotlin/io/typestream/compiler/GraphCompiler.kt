package io.typestream.compiler

import io.typestream.compiler.ast.PredicateParser
import io.typestream.compiler.node.*
import io.typestream.compiler.types.DataStream
import io.typestream.compiler.types.Encoding
import io.typestream.compiler.types.FileSystemInferenceContext
import io.typestream.compiler.types.InferenceContext
import io.typestream.compiler.types.InferenceResult
import io.typestream.compiler.types.schema.Schema
import io.typestream.filesystem.FileSystem
import io.typestream.graph.Graph
import io.typestream.grpc.job_service.Job
import java.util.ArrayDeque
import java.util.UUID

class GraphCompiler(private val fileSystem: FileSystem) {

  private val context: InferenceContext = FileSystemInferenceContext(fileSystem)

  private fun findDataStreamOrError(path: String): DataStream =
    fileSystem.findDataStream(path) ?: error("No DataStream for path: $path")

  fun compile(request: Job.CreateJobFromGraphRequest): Program {
    return compileFromGraph(request.graph)
  }

  fun compileFromGraph(graphProto: Job.PipelineGraph, programId: String? = null): Program {
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
    validateNoSelfLoop(graphProto)
    val fromProtoContext = FromProtoContext(inferredSchemas, inferredEncodings, ::findDataStreamOrError)
    val rootGraphs = sources.map { buildGraph(it, nodesById, adjList, fromProtoContext) }
    val programGraph = if (rootGraphs.size == 1) rootGraphs.first() else error("Multi-source not supported yet")
    val root: Graph<Node> = Graph(NodeNoOp("root"))
    root.addChild(programGraph)
    Infer.infer(root)  // Validation still works!
    val id = programId ?: UUID.randomUUID().toString()
    return Program(id, root, graphProto)
  }

  private fun buildGraph(
    id: String,
    nodesById: Map<String, Job.PipelineNode>,
    adjList: MutableMap<String, MutableList<String>>,
    fromProtoContext: FromProtoContext
  ): Graph<Node> {
    val protoNode = nodesById[id] ?: error("Missing node $id")
    val node = Node.fromProto(protoNode, fromProtoContext)
    val children = adjList[id]?.map { buildGraph(it, nodesById, adjList, fromProtoContext) }?.toSet() ?: emptySet()
    return Graph(node, children.toMutableSet())
  }

  /**
   * Phase 1: Infer schemas and encodings for all nodes in the graph.
   * This creates two in-memory maps:
   * - node ID -> DataStream with inferred schema
   * - node ID -> Encoding (propagated from sources through the pipeline)
   * These are then used in Phase 2 (buildGraph) to construct nodes.
   * Also exposed for UI schema inference endpoint.
   */
  fun inferNodeSchemasAndEncodings(graph: Job.PipelineGraph): Pair<Map<String, DataStream>, Map<String, Encoding>> {
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
   * UI-friendly version of schema inference that handles errors gracefully.
   * Returns schemas and errors per-node without throwing exceptions.
   * When a node fails validation, it still stores the input schema so
   * downstream nodes can populate field dropdowns.
   */
  data class NodeInferenceResult(
    val schema: DataStream?,
    val encoding: Encoding?,
    val error: String?
  )

  fun inferNodeSchemasForUI(graph: Job.PipelineGraph): Map<String, NodeInferenceResult> {
    val results = mutableMapOf<String, NodeInferenceResult>()
    val nodesById = graph.nodesList.associateBy { it.id }
    val adjList = mutableMapOf<String, MutableList<String>>()

    graph.edgesList.forEach { edge ->
      adjList.getOrPut(edge.fromId) { mutableListOf() }.add(edge.toId)
    }

    val sources = nodesById.keys - adjList.values.flatten().toSet()

    sources.forEach { sourceId ->
      inferNodeTypeForUI(sourceId, nodesById, adjList, null, null, results)
    }

    return results
  }

  private fun inferNodeTypeForUI(
    nodeId: String,
    nodesById: Map<String, Job.PipelineNode>,
    adjList: Map<String, List<String>>,
    input: DataStream?,
    inputEncoding: Encoding?,
    results: MutableMap<String, NodeInferenceResult>
  ) {
    val proto = nodesById[nodeId]
    if (proto == null) {
      results[nodeId] = NodeInferenceResult(null, null, "Missing node $nodeId")
      return
    }

    try {
      val result = inferSingleNodeType(proto, nodeId, input, inputEncoding)
      results[nodeId] = NodeInferenceResult(result.dataStream, result.encoding, null)

      // Recursively process children
      adjList[nodeId]?.forEach { childId ->
        inferNodeTypeForUI(childId, nodesById, adjList, result.dataStream, result.encoding, results)
      }
    } catch (e: Exception) {
      // On error, store the INPUT schema (for field dropdown population) and the error message
      results[nodeId] = NodeInferenceResult(input, inputEncoding, e.message ?: "Inference failed")

      // Still try to process children using the input schema (pass-through on error)
      adjList[nodeId]?.forEach { childId ->
        inferNodeTypeForUI(childId, nodesById, adjList, input, inputEncoding, results)
      }
    }
  }

  /**
   * Infer the type for a single node by creating a temporary Node and calling its inferOutputSchema method.
   * Source nodes require special handling for catalog lookups and CDC unwrapping.
   */
  private fun inferSingleNodeType(
    proto: Job.PipelineNode,
    nodeId: String,
    input: DataStream?,
    inputEncoding: Encoding?
  ): InferenceResult {
    // Create temporary node for inference
    val tempNode: Node = when {
      proto.hasStreamSource() -> {
        val path = proto.streamSource.dataStream.path
        val ds = context.lookupDataStream(path)
        val enc = context.lookupEncoding(path)
        // CDC unwrapping is handled in StreamSource.inferOutputSchema
        NodeStreamSource(nodeId, ds, enc, proto.streamSource.unwrapCdc)
      }
      proto.hasShellSource() -> {
        val dataStreams = proto.shellSource.dataList.map { dsProto ->
          context.lookupDataStream(dsProto.path)
        }
        NodeShellSource(nodeId, dataStreams)
      }
      proto.hasFilter() -> {
        val f = proto.filter
        NodeFilter(nodeId, f.byKey, PredicateParser.parse(f.predicate.expr))
      }
      proto.hasMap() -> NodeMap(nodeId) { kv -> kv }
      proto.hasJoin() -> {
        val withPath = proto.join.with.path
        val withStream = context.lookupDataStream(withPath)
        NodeJoin(nodeId, withStream, JoinType(proto.join.joinType.byKey, proto.join.joinType.isLookup))
      }
      proto.hasGroup() -> {
        val fieldPath = proto.group.keyMapperExpr
        val fields = fieldPath.trimStart('.').split('.').filter { it.isNotBlank() }
        NodeGroup(nodeId) { kv -> kv.value.select(fields) }
      }
      proto.hasCount() -> NodeCount(nodeId)
      proto.hasWindowedCount() -> NodeWindowedCount(nodeId, proto.windowedCount.windowSizeSeconds)
      proto.hasEach() -> NodeEach(nodeId) { _ -> }
      proto.hasSink() -> {
        // Sink needs a placeholder output DataStream with the target path
        val targetPath = proto.sink.output.path
        val placeholderOutput = DataStream(targetPath, Schema.String.zeroValue)
        NodeSink(nodeId, placeholderOutput, inputEncoding ?: Encoding.AVRO)
      }
      proto.hasNoop() -> NodeNoOp(nodeId)
      proto.hasGeoIp() -> {
        val g = proto.geoIp
        NodeGeoIp(nodeId, g.ipField, g.outputField)
      }
      proto.hasInspector() -> NodeInspector(nodeId, proto.inspector.label)
      proto.hasReduceLatest() -> NodeReduceLatest(nodeId)
      proto.hasTextExtractor() -> {
        val t = proto.textExtractor
        NodeTextExtractor(nodeId, t.filePathField, t.outputField.ifBlank { "text" })
      }
      proto.hasEmbeddingGenerator() -> {
        val e = proto.embeddingGenerator
        NodeEmbeddingGenerator(nodeId, e.textField, e.outputField.ifBlank { "embedding" }, e.model)
      }
      proto.hasOpenAiTransformer() -> {
        val o = proto.openAiTransformer
        NodeOpenAiTransformer(nodeId, o.prompt, o.outputField.ifBlank { "ai_response" }, o.model)
      }
      else -> error("Unknown node type: $nodeId")
    }

    // Delegate to the node's inferOutputSchema method
    return tempNode.inferOutputSchema(input, inputEncoding, context)
  }

  /**
   * Recursively infer the output schema and encoding for a node and its children.
   * Uses node methods for all type transformations to ensure consistency.
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
    val result = inferSingleNodeType(proto, nodeId, input, inputEncoding)

    schemas[nodeId] = result.dataStream
    encodings[nodeId] = result.encoding

    // Recursively process children with propagated encoding
    adjList[nodeId]?.forEach { childId ->
      inferNodeType(childId, nodesById, adjList, result.dataStream, result.encoding, schemas, encodings)
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

  private fun validateNoSelfLoop(graph: Job.PipelineGraph) {
    val sourcePaths = graph.nodesList
      .filter { it.hasStreamSource() }
      .map { it.streamSource.dataStream.path }
      .toSet()

    val sinkPaths = graph.nodesList
      .filter { it.hasSink() }
      .map { it.sink.output.path }

    val conflicts = sinkPaths.filter { it in sourcePaths }
    if (conflicts.isNotEmpty()) {
      error("Cannot write to the same topic being read: ${conflicts.joinToString(", ")}")
    }
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

fun Encoding.toProtoEncoding(): Job.Encoding = when (this) {
  Encoding.STRING -> Job.Encoding.STRING
  Encoding.NUMBER -> Job.Encoding.NUMBER
  Encoding.JSON -> Job.Encoding.JSON
  Encoding.AVRO -> Job.Encoding.AVRO
  Encoding.PROTOBUF -> Job.Encoding.PROTOBUF
}
