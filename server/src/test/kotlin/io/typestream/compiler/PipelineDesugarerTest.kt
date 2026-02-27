package io.typestream.compiler

import io.typestream.grpc.job_service.Job
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class PipelineDesugarerTest {

    @Test
    fun `non-windowed count emits TableMaterializedNode`() {
        val userGraph = userGraph(
            nodes = listOf(
                kafkaSourceNode("src-1", "/local/topics/orders", Job.Encoding.AVRO),
                materializedViewNode("mv-1", groupByField = "status", aggregationType = "count")
            ),
            edges = listOf(edge("src-1", "mv-1"))
        )

        val result = PipelineDesugarer.desugar(userGraph)

        // Should have 2 nodes: StreamSource + TableMaterialized (no Group node)
        assertThat(result.graph.nodesList).hasSize(2)
        val tableMat = result.graph.nodesList.find { it.hasTableMaterialized() }
        assertThat(tableMat).isNotNull
        assertThat(tableMat!!.tableMaterialized.groupByField).isEqualTo("status")
        assertThat(tableMat.tableMaterialized.aggregationType).isEqualTo("count")

        // No group node should exist
        assertThat(result.graph.nodesList.none { it.hasGroup() }).isTrue()

        // Edge goes directly from source to table materialized
        assertThat(result.graph.edgesList).hasSize(1)
        assertThat(result.graph.edgesList[0].fromId).isEqualTo("src-1")
        assertThat(result.graph.edgesList[0].toId).isEqualTo("mv-1")
    }

    @Test
    fun `non-windowed latest emits TableMaterializedNode`() {
        val userGraph = userGraph(
            nodes = listOf(
                kafkaSourceNode("src-1", "/local/topics/users", Job.Encoding.AVRO),
                materializedViewNode("mv-1", groupByField = "department", aggregationType = "latest")
            ),
            edges = listOf(edge("src-1", "mv-1"))
        )

        val result = PipelineDesugarer.desugar(userGraph)

        val tableMat = result.graph.nodesList.find { it.hasTableMaterialized() }
        assertThat(tableMat).isNotNull
        assertThat(tableMat!!.tableMaterialized.groupByField).isEqualTo("department")
        assertThat(tableMat.tableMaterialized.aggregationType).isEqualTo("latest")
    }

    @Test
    fun `windowed count emits Group and WindowedCount nodes`() {
        val userGraph = userGraph(
            nodes = listOf(
                kafkaSourceNode("src-1", "/local/topics/events", Job.Encoding.AVRO),
                materializedViewNode(
                    "mv-1",
                    groupByField = "event_type",
                    aggregationType = "count",
                    enableWindowing = true,
                    windowSizeSeconds = 60
                )
            ),
            edges = listOf(edge("src-1", "mv-1"))
        )

        val result = PipelineDesugarer.desugar(userGraph)

        // Should have 3 nodes: StreamSource + Group + WindowedCount
        assertThat(result.graph.nodesList).hasSize(3)
        assertThat(result.graph.nodesList.any { it.hasGroup() }).isTrue()
        assertThat(result.graph.nodesList.any { it.hasWindowedCount() }).isTrue()
        // No TableMaterialized node
        assertThat(result.graph.nodesList.none { it.hasTableMaterialized() }).isTrue()

        // Group node should have correct keyMapperExpr
        val group = result.graph.nodesList.find { it.hasGroup() }!!
        assertThat(group.group.keyMapperExpr).isEqualTo(".event_type")

        // Edge from source goes to group (redirectMap)
        assertThat(result.graph.edgesList.any { it.fromId == "src-1" && it.toId == "mv-1-group" }).isTrue()
    }

    @Test
    fun `non-windowed latest with empty groupByField emits TableMaterializedNode`() {
        val userGraph = userGraph(
            nodes = listOf(
                kafkaSourceNode("src-1", "/local/topics/config", Job.Encoding.AVRO),
                materializedViewNode("mv-1", groupByField = "", aggregationType = "latest")
            ),
            edges = listOf(edge("src-1", "mv-1"))
        )

        val result = PipelineDesugarer.desugar(userGraph)

        val tableMat = result.graph.nodesList.find { it.hasTableMaterialized() }
        assertThat(tableMat).isNotNull
        assertThat(tableMat!!.tableMaterialized.groupByField).isEmpty()
        assertThat(tableMat.tableMaterialized.aggregationType).isEqualTo("latest")
    }

    // --- Helper methods ---

    private fun kafkaSourceNode(id: String, topicPath: String, encoding: Job.Encoding): Job.UserPipelineNode =
        Job.UserPipelineNode.newBuilder()
            .setId(id)
            .setKafkaSource(
                Job.KafkaSourceNode.newBuilder()
                    .setTopicPath(topicPath)
                    .setEncoding(encoding)
            )
            .build()

    private fun materializedViewNode(
        id: String,
        groupByField: String,
        aggregationType: String,
        enableWindowing: Boolean = false,
        windowSizeSeconds: Long = 0
    ): Job.UserPipelineNode =
        Job.UserPipelineNode.newBuilder()
            .setId(id)
            .setMaterializedView(
                Job.MaterializedViewNode.newBuilder()
                    .setGroupByField(groupByField)
                    .setAggregationType(aggregationType)
                    .setEnableWindowing(enableWindowing)
                    .setWindowSizeSeconds(windowSizeSeconds)
            )
            .build()

    private fun edge(from: String, to: String): Job.PipelineEdge =
        Job.PipelineEdge.newBuilder().setFromId(from).setToId(to).build()

    private fun userGraph(
        nodes: List<Job.UserPipelineNode>,
        edges: List<Job.PipelineEdge>
    ): Job.UserPipelineGraph {
        val builder = Job.UserPipelineGraph.newBuilder()
        nodes.forEach(builder::addNodes)
        edges.forEach(builder::addEdges)
        return builder.build()
    }
}
