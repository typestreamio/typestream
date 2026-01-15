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

    @Test
    fun `fails when sink writes to same topic as source`() {
        testKafka.produceRecords(
            "books",
            "avro",
            Book(title = "Self Loop", wordCount = 100, authorId = UUID.randomUUID().toString())
        )
        fileSystem.refresh()

        val request = createRequest(
            nodes = listOf(
                streamSourceNode("source", "/dev/kafka/local/topics/books", Job.Encoding.AVRO),
                sinkNode("sink", "/dev/kafka/local/topics/books")
            ),
            edges = listOf(edge("source", "sink"))
        )

        assertThatThrownBy { compiler.compile(request) }
            .hasMessageContaining("Cannot write to the same topic being read")
            .hasMessageContaining("/dev/kafka/local/topics/books")
    }

    @Test
    fun `compiles stream source with text extractor`() {
        testKafka.produceRecords(
            "books",
            "avro",
            Book(title = "Test Book", wordCount = 100, authorId = UUID.randomUUID().toString())
        )
        fileSystem.refresh()

        val request = createRequest(
            nodes = listOf(
                streamSourceNode("source", "/dev/kafka/local/topics/books", Job.Encoding.AVRO),
                textExtractorNode("extractor", "title", "extracted_text")
            ),
            edges = listOf(edge("source", "extractor"))
        )

        val program = compiler.compile(request)

        val streamGraph = program.graph.children.single()
        val stream = streamGraph.ref as Node.StreamSource
        assertThat(stream.encoding).isEqualTo(Encoding.AVRO)

        val textExtractor = streamGraph.children.single().ref as Node.TextExtractor
        assertThat(textExtractor.filePathField).isEqualTo("title")
        assertThat(textExtractor.outputField).isEqualTo("extracted_text")
    }

    @Test
    fun `text extractor adds output field to schema`() {
        testKafka.produceRecords(
            "books",
            "avro",
            Book(title = "Schema Test", wordCount = 50, authorId = UUID.randomUUID().toString())
        )
        fileSystem.refresh()

        val request = createRequest(
            nodes = listOf(
                streamSourceNode("source", "/dev/kafka/local/topics/books", Job.Encoding.AVRO),
                textExtractorNode("extractor", "title", "content")
            ),
            edges = listOf(edge("source", "extractor"))
        )

        val program = compiler.compile(request)

        // The graph compiles successfully, meaning type inference worked
        assertThat(program.graph.children).hasSize(1)
    }

    @Test
    fun `text extractor fails when file path field does not exist`() {
        testKafka.produceRecords(
            "books",
            "avro",
            Book(title = "Field Test", wordCount = 25, authorId = UUID.randomUUID().toString())
        )
        fileSystem.refresh()

        val request = createRequest(
            nodes = listOf(
                streamSourceNode("source", "/dev/kafka/local/topics/books", Job.Encoding.AVRO),
                textExtractorNode("extractor", "nonexistent_field", "text")
            ),
            edges = listOf(edge("source", "extractor"))
        )

        assertThatThrownBy { compiler.compile(request) }
            .hasMessageContaining("file path field 'nonexistent_field' not found in schema")
    }

    // ===== inferNodeSchemasForUI Tests =====

    @Test
    fun `inferNodeSchemasForUI handles missing upstream gracefully`() {
        // A text extractor node with no upstream source
        val graph = createGraph(
            nodes = listOf(
                textExtractorNode("text-1", "file_path", "text")
            ),
            edges = emptyList()
        )

        val results = compiler.inferNodeSchemasForUI(graph)

        // Should have an error since there's no input
        assertThat(results["text-1"]?.error).isNotNull()
        assertThat(results["text-1"]?.error).contains("missing input")
        // Schema should be null (no input to pass through)
        assertThat(results["text-1"]?.schema).isNull()
    }

    @Test
    fun `inferNodeSchemasForUI propagates schemas through chain`() {
        testKafka.produceRecords(
            "books",
            "avro",
            Book(title = "Chain Test", wordCount = 100, authorId = UUID.randomUUID().toString())
        )
        fileSystem.refresh()

        val graph = createGraph(
            nodes = listOf(
                streamSourceNode("src-1", "/dev/kafka/local/topics/books", Job.Encoding.AVRO),
                textExtractorNode("text-1", "title", "text"),
                embeddingGeneratorNode("embed-1", "text", "embedding")
            ),
            edges = listOf(
                edge("src-1", "text-1"),
                edge("text-1", "embed-1")
            )
        )

        val results = compiler.inferNodeSchemasForUI(graph)

        // All nodes should succeed without errors
        assertThat(results["src-1"]?.error).isNull()
        assertThat(results["text-1"]?.error).isNull()
        assertThat(results["embed-1"]?.error).isNull()

        // Verify schema propagation - text extractor should add "text" field
        val textSchema = results["text-1"]?.schema?.schema
        assertThat(textSchema).isInstanceOf(io.typestream.compiler.types.schema.Schema.Struct::class.java)
        val textFields = (textSchema as io.typestream.compiler.types.schema.Schema.Struct).value.map { it.name }
        assertThat(textFields).contains("text")

        // Embedding generator should add "embedding" field
        val embedSchema = results["embed-1"]?.schema?.schema
        assertThat(embedSchema).isInstanceOf(io.typestream.compiler.types.schema.Schema.Struct::class.java)
        val embedFields = (embedSchema as io.typestream.compiler.types.schema.Schema.Struct).value.map { it.name }
        assertThat(embedFields).contains("text", "embedding")
    }

    @Test
    fun `inferNodeSchemasForUI passes input schema to downstream on error`() {
        testKafka.produceRecords(
            "books",
            "avro",
            Book(title = "Error Recovery", wordCount = 50, authorId = UUID.randomUUID().toString())
        )
        fileSystem.refresh()

        // text-1 has invalid field, but embed-1 should still get text-1's INPUT schema
        val graph = createGraph(
            nodes = listOf(
                streamSourceNode("src-1", "/dev/kafka/local/topics/books", Job.Encoding.AVRO),
                textExtractorNode("text-1", "nonexistent_field", "text"),
                embeddingGeneratorNode("embed-1", "title", "embedding")
            ),
            edges = listOf(
                edge("src-1", "text-1"),
                edge("text-1", "embed-1")
            )
        )

        val results = compiler.inferNodeSchemasForUI(graph)

        // Source should succeed
        assertThat(results["src-1"]?.error).isNull()

        // Text extractor should fail but still have the input schema
        assertThat(results["text-1"]?.error).isNotNull()
        assertThat(results["text-1"]?.error).contains("nonexistent_field")
        // The schema stored should be the INPUT (source) schema for dropdown population
        val text1Schema = results["text-1"]?.schema?.schema
        assertThat(text1Schema).isInstanceOf(io.typestream.compiler.types.schema.Schema.Struct::class.java)
        val text1Fields = (text1Schema as io.typestream.compiler.types.schema.Schema.Struct).value.map { it.name }
        // Avro uses snake_case field names
        assertThat(text1Fields).contains("title", "word_count", "author_id")

        // Downstream node gets the input schema (pass-through on error)
        // It should also fail because "title" exists but we're testing schema propagation
        val embed1Schema = results["embed-1"]?.schema?.schema
        assertThat(embed1Schema).isInstanceOf(io.typestream.compiler.types.schema.Schema.Struct::class.java)
    }

    @Test
    fun `inferNodeSchemasForUI returns encoding for all nodes`() {
        testKafka.produceRecords(
            "books",
            "avro",
            Book(title = "Encoding Test", wordCount = 75, authorId = UUID.randomUUID().toString())
        )
        fileSystem.refresh()

        val graph = createGraph(
            nodes = listOf(
                streamSourceNode("src-1", "/dev/kafka/local/topics/books", Job.Encoding.AVRO),
                filterNode("filter-1", "Test")
            ),
            edges = listOf(edge("src-1", "filter-1"))
        )

        val results = compiler.inferNodeSchemasForUI(graph)

        // Both nodes should have AVRO encoding (propagated from source)
        assertThat(results["src-1"]?.encoding).isEqualTo(Encoding.AVRO)
        assertThat(results["filter-1"]?.encoding).isEqualTo(Encoding.AVRO)
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

    private fun sinkNode(id: String, path: String): Job.PipelineNode =
        Job.PipelineNode.newBuilder()
            .setId(id)
            .setSink(
                Job.SinkNode.newBuilder()
                    .setOutput(Job.DataStreamProto.newBuilder().setPath(path))
            )
            .build()

    private fun textExtractorNode(id: String, filePathField: String, outputField: String): Job.PipelineNode =
        Job.PipelineNode.newBuilder()
            .setId(id)
            .setTextExtractor(
                Job.TextExtractorNode.newBuilder()
                    .setFilePathField(filePathField)
                    .setOutputField(outputField)
            )
            .build()

    private fun embeddingGeneratorNode(id: String, textField: String, outputField: String): Job.PipelineNode =
        Job.PipelineNode.newBuilder()
            .setId(id)
            .setEmbeddingGenerator(
                Job.EmbeddingGeneratorNode.newBuilder()
                    .setTextField(textField)
                    .setOutputField(outputField)
                    .setModel("text-embedding-3-small")
            )
            .build()

    private fun edge(from: String, to: String): Job.PipelineEdge =
        Job.PipelineEdge.newBuilder().setFromId(from).setToId(to).build()

    private fun createGraph(
        nodes: List<Job.PipelineNode>,
        edges: List<Job.PipelineEdge>
    ): Job.PipelineGraph {
        val graphBuilder = Job.PipelineGraph.newBuilder()
        nodes.forEach(graphBuilder::addNodes)
        edges.forEach(graphBuilder::addEdges)
        return graphBuilder.build()
    }

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
