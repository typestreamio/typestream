package io.typestream.compiler

import io.typestream.compiler.ast.Predicate
import io.typestream.compiler.node.Node
import io.typestream.compiler.types.DataStream
import io.typestream.compiler.types.Encoding
import io.typestream.compiler.types.datastream.fromAvroSchema
import io.typestream.config.testing.testConfig
import io.typestream.filesystem.FileSystem
import io.typestream.grpc.job_service.Job
import io.typestream.testing.TestKafka
import io.typestream.testing.model.Book
import kotlinx.coroutines.Dispatchers
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.util.UUID
import io.typestream.testing.avro.Book as AvroBook

@Testcontainers
internal class GraphCompilerTest {
    @Container
    private val testKafka = TestKafka()

    private lateinit var fileSystem: FileSystem
    private lateinit var compiler: GraphCompiler

    @BeforeEach
    fun setUp() {
        fileSystem = FileSystem(testConfig(testKafka), Dispatchers.IO)
        compiler = GraphCompiler(fileSystem)
    }

    @Test
    fun `compiles stream source with filter`() {
        testKafka.produceRecords(
            "books",
            "avro",
            Book(title = "Station Eleven", wordCount = 300, authorId = UUID.randomUUID().toString())
        )
        fileSystem.refresh()

        val request = createRequest(
            nodes = listOf(
                streamSourceNode("source", "/dev/kafka/local/topics/books", Job.Encoding.AVRO),
                filterNode("filter", "Station Eleven")
            ),
            edges = listOf(edge("source", "filter"))
        )

        val program = compiler.compile(request)

        val streamGraph = program.graph.children.single()
        val stream = streamGraph.ref as Node.StreamSource
        assertThat(stream.encoding).isEqualTo(Encoding.AVRO)
        assertThat(stream.dataStream).isEqualTo(
            DataStream.fromAvroSchema("/dev/kafka/local/topics/books", AvroBook.getClassSchema())
        )

        val filter = streamGraph.children.single().ref as Node.Filter
        assertThat(filter.byKey).isFalse()
        assertThat(filter.predicate).isEqualTo(Predicate.matches("Station Eleven"))
    }

    @Test
    fun `fails on cyclic graph`() {
        testKafka.produceRecords(
            "books",
            "avro",
            Book(title = "Cycle", wordCount = 1, authorId = UUID.randomUUID().toString())
        )
        fileSystem.refresh()

        val request = createRequest(
            nodes = listOf(
                streamSourceNode("source", "/dev/kafka/local/topics/books", Job.Encoding.AVRO),
                filterNode("filter", "anything")
            ),
            edges = listOf(edge("source", "filter"), edge("filter", "source"))
        )

        assertThatThrownBy { compiler.compile(request) }.hasMessageContaining("Cycle detected")
    }

    @Test
    fun `requires source before filter`() {
        val graph = Job.PipelineGraph.newBuilder()
            .addNodes(filterNode("filter", "dangling"))
            .build()

        val request = Job.CreateJobFromGraphRequest.newBuilder()
            .setUserId("user")
            .setGraph(graph)
            .build()

        assertThatThrownBy { compiler.compile(request) }.hasMessageContaining("filter")
    }

    private fun streamSourceNode(id: String, path: String, encoding: Job.Encoding): Job.PipelineNode =
        Job.PipelineNode.newBuilder()
            .setId(id)
            .setStreamSource(
                Job.StreamSourceNode.newBuilder()
                    .setDataStream(Job.DataStreamProto.newBuilder().setPath(path))
                    .setEncoding(encoding)
            )
            .build()

    private fun filterNode(id: String, expr: String, byKey: Boolean = false): Job.PipelineNode =
        Job.PipelineNode.newBuilder()
            .setId(id)
            .setFilter(
                Job.FilterNode.newBuilder()
                    .setByKey(byKey)
                    .setPredicate(Job.PredicateProto.newBuilder().setExpr(expr))
            )
            .build()

    private fun edge(from: String, to: String): Job.PipelineEdge =
        Job.PipelineEdge.newBuilder().setFromId(from).setToId(to).build()

    private fun createRequest(
        nodes: List<Job.PipelineNode>,
        edges: List<Job.PipelineEdge>
    ): Job.CreateJobFromGraphRequest {
        val graphBuilder = Job.PipelineGraph.newBuilder()
        nodes.forEach(graphBuilder::addNodes)
        edges.forEach(graphBuilder::addEdges)

        return Job.CreateJobFromGraphRequest.newBuilder()
            .setUserId("user")
            .setGraph(graphBuilder.build())
            .build()
    }
}
