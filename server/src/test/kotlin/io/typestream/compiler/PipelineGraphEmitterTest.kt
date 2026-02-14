package io.typestream.compiler

import io.typestream.compiler.ast.PredicateParser
import io.typestream.compiler.node.Node
import io.typestream.compiler.node.NodeFilter
import io.typestream.compiler.node.NodeSink
import io.typestream.compiler.node.NodeStreamSource
import io.typestream.compiler.parser.Parser
import io.typestream.compiler.types.Encoding
import io.typestream.compiler.vm.Env
import io.typestream.compiler.vm.Session
import io.typestream.config.testing.testConfig
import io.typestream.filesystem.FileSystem
import io.typestream.scheduler.Scheduler
import io.typestream.testing.TestKafka
import io.typestream.testing.TestKafkaContainer
import io.typestream.testing.model.Book
import kotlinx.coroutines.Dispatchers
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.testcontainers.junit.jupiter.Testcontainers
import java.util.UUID

@Testcontainers
internal class PipelineGraphEmitterTest {

    companion object {
        private val testKafka = TestKafkaContainer.instance
    }

    private lateinit var fileSystem: FileSystem
    private lateinit var session: Session

    @BeforeEach
    fun beforeEach() {
        val testConfig = testConfig(testKafka)
        fileSystem = FileSystem(testConfig, Dispatchers.IO)
        session = Session(fileSystem, Scheduler(false, Dispatchers.IO), Env(testConfig))
    }

    private fun interpretAndEmit(source: String): io.typestream.grpc.job_service.Job.PipelineGraph? {
        val statements = Parser(source).parse()
        val interpreter = Interpreter(session)
        statements.forEach { it.accept(interpreter) }
        assertThat(interpreter.errors).isEmpty()
        return PipelineGraphEmitter().emit(statements)
    }

    @Nested
    inner class CatCommand {
        @Test
        fun `emits StreamSourceNode for cat`() {
            val topic = TestKafka.uniqueTopic("books")
            testKafka.produceRecords(
                topic,
                "avro",
                Book(title = "Test", wordCount = 100, authorId = UUID.randomUUID().toString())
            )
            fileSystem.refresh()

            val graph = interpretAndEmit("cat /dev/kafka/local/topics/$topic")

            assertThat(graph).isNotNull
            assertThat(graph!!.nodesCount).isEqualTo(1)
            val node = graph.nodesList.first()
            assertThat(node.hasStreamSource()).isTrue()
            assertThat(node.streamSource.dataStream.path).isEqualTo("/dev/kafka/local/topics/$topic")
        }
    }

    @Nested
    inner class GrepCommand {
        private lateinit var topic: String

        @BeforeEach
        fun beforeEach() {
            topic = TestKafka.uniqueTopic("books")
            testKafka.produceRecords(
                topic,
                "avro",
                Book(title = "Station Eleven", wordCount = 300, authorId = UUID.randomUUID().toString())
            )
            fileSystem.refresh()
        }

        @Test
        fun `emits StreamSource and Filter for piped grep`() {
            val graph = interpretAndEmit("cat /dev/kafka/local/topics/$topic | grep 'Station Eleven'")

            assertThat(graph).isNotNull
            assertThat(graph!!.nodesCount).isEqualTo(2)

            val sourceNode = graph.nodesList.first { it.hasStreamSource() }
            assertThat(sourceNode.streamSource.dataStream.path).isEqualTo("/dev/kafka/local/topics/$topic")

            val filterNode = graph.nodesList.first { it.hasFilter() }
            assertThat(filterNode.filter.predicate.expr).isEqualTo("Station Eleven")

            assertThat(graph.edgesCount).isEqualTo(1)
            assertThat(graph.edgesList.first().fromId).isEqualTo(sourceNode.id)
            assertThat(graph.edgesList.first().toId).isEqualTo(filterNode.id)
        }

        @Test
        fun `emits StreamSource and Filter for standalone grep`() {
            val graph = interpretAndEmit("grep 'Station Eleven' /dev/kafka/local/topics/$topic")

            assertThat(graph).isNotNull
            assertThat(graph!!.nodesCount).isEqualTo(2)

            val sourceNode = graph.nodesList.first { it.hasStreamSource() }
            val filterNode = graph.nodesList.first { it.hasFilter() }

            assertThat(graph.edgesCount).isEqualTo(1)
            assertThat(graph.edgesList.first().fromId).isEqualTo(sourceNode.id)
            assertThat(graph.edgesList.first().toId).isEqualTo(filterNode.id)
        }

        @Test
        fun `emits inverted grep with Not predicate`() {
            val graph = interpretAndEmit("grep -v 'Station Eleven' /dev/kafka/local/topics/$topic")

            assertThat(graph).isNotNull
            val filterNode = graph!!.nodesList.first { it.hasFilter() }
            // Verify the expression round-trips correctly
            val predicate = PredicateParser.parse(filterNode.filter.predicate.expr)
            assertThat(predicate).isEqualTo(
                io.typestream.compiler.ast.Predicate.matches("Station Eleven").not()
            )
        }

        @Test
        fun `emits grep by key`() {
            val graph = interpretAndEmit("grep -k 'Station Eleven' /dev/kafka/local/topics/$topic")

            assertThat(graph).isNotNull
            val filterNode = graph!!.nodesList.first { it.hasFilter() }
            assertThat(filterNode.filter.byKey).isTrue()
        }

        @Test
        fun `emits grep with structured predicate`() {
            val graph = interpretAndEmit("cat /dev/kafka/local/topics/$topic | grep [.title ~= 'the']")

            assertThat(graph).isNotNull
            val filterNode = graph!!.nodesList.first { it.hasFilter() }
            val predicate = PredicateParser.parse(filterNode.filter.predicate.expr)
            assertThat(predicate).isEqualTo(
                io.typestream.compiler.ast.Predicate.almostEquals("title", io.typestream.compiler.types.schema.Schema.String("the"))
            )
        }
    }

    @Nested
    inner class CutCommand {
        @Test
        fun `emits MapNode with select expression for cut`() {
            val topic = TestKafka.uniqueTopic("books")
            testKafka.produceRecords(
                topic,
                "avro",
                Book(title = "Test", wordCount = 100, authorId = UUID.randomUUID().toString())
            )
            fileSystem.refresh()

            val graph = interpretAndEmit("cat /dev/kafka/local/topics/$topic | cut .title .word_count")

            assertThat(graph).isNotNull
            assertThat(graph!!.nodesCount).isEqualTo(2)

            val mapNode = graph.nodesList.first { it.hasMap() }
            assertThat(mapNode.map.mapperExpr).isEqualTo("select .title .word_count")
        }
    }

    @Nested
    inner class WcCommand {
        @Test
        fun `emits Group and Count nodes for wc with by`() {
            val topic = TestKafka.uniqueTopic("books")
            testKafka.produceRecords(
                topic,
                "avro",
                Book(title = "Test", wordCount = 100, authorId = UUID.randomUUID().toString())
            )
            fileSystem.refresh()

            val graph = interpretAndEmit("cat /dev/kafka/local/topics/$topic | wc --by .author_id")

            assertThat(graph).isNotNull
            // StreamSource + Group + Count = 3 nodes
            assertThat(graph!!.nodesCount).isEqualTo(3)

            val groupNode = graph.nodesList.first { it.hasGroup() }
            assertThat(groupNode.group.keyMapperExpr).isEqualTo(".author_id")

            val countNode = graph.nodesList.first { it.hasCount() }
            assertThat(countNode).isNotNull

            // Verify edges: source -> group -> count
            assertThat(graph.edgesCount).isEqualTo(2)
        }

        @Test
        fun `emits Group and Count nodes for wc without by`() {
            val topic = TestKafka.uniqueTopic("books")
            testKafka.produceRecords(
                topic,
                "avro",
                Book(title = "Test", wordCount = 100, authorId = UUID.randomUUID().toString())
            )
            fileSystem.refresh()

            val graph = interpretAndEmit("cat /dev/kafka/local/topics/$topic | wc")

            assertThat(graph).isNotNull
            assertThat(graph!!.nodesCount).isEqualTo(3)

            val groupNode = graph.nodesList.first { it.hasGroup() }
            assertThat(groupNode.group.keyMapperExpr).isEmpty()
        }
    }

    @Nested
    inner class Redirections {
        @Test
        fun `emits SinkNode for redirection`() {
            val topic = TestKafka.uniqueTopic("books")
            testKafka.produceRecords(
                topic,
                "avro",
                Book(title = "Test", wordCount = 100, authorId = UUID.randomUUID().toString())
            )
            fileSystem.refresh()

            val graph = interpretAndEmit(
                "cat /dev/kafka/local/topics/$topic | grep 'test' > /dev/kafka/local/topics/output-topic"
            )

            assertThat(graph).isNotNull
            val sinkNode = graph!!.nodesList.first { it.hasSink() }
            assertThat(sinkNode.sink.output.path).isEqualTo("/dev/kafka/local/topics/output-topic")
        }
    }

    @Nested
    inner class UnsupportedCommands {
        @Test
        fun `returns null for enrich with block`() {
            val topic = TestKafka.uniqueTopic("books")
            testKafka.produceRecords(
                topic,
                "avro",
                Book(title = "Test", wordCount = 100, authorId = UUID.randomUUID().toString())
            )
            fileSystem.refresh()

            val graph = interpretAndEmit(
                "cat /dev/kafka/local/topics/$topic | enrich { v -> echo \$v.title }"
            )

            assertThat(graph).isNull()
        }
    }

    @Nested
    inner class RoundTrip {
        @Test
        fun `round-trips cat through PipelineGraph and GraphCompiler`() {
            val topic = TestKafka.uniqueTopic("books")
            testKafka.produceRecords(
                topic,
                "avro",
                Book(title = "Round Trip", wordCount = 200, authorId = UUID.randomUUID().toString())
            )
            fileSystem.refresh()

            val source = "cat /dev/kafka/local/topics/$topic"

            // Compile via the unified path (PipelineGraphEmitter → GraphCompiler)
            val compiler = Compiler(session)
            val result = compiler.compile(source)

            assertThat(result.errors).isEmpty()
            assertThat(result.program.graph.children).hasSize(1)

            val streamNode = result.program.graph.children.first().ref
            assertThat(streamNode).isInstanceOf(NodeStreamSource::class.java)
            assertThat((streamNode as NodeStreamSource).encoding).isEqualTo(Encoding.AVRO)

            // Verify the PipelineGraph is stored on the program
            assertThat(result.program.pipelineGraph).isNotNull
        }

        @Test
        fun `round-trips cat with grep through unified path`() {
            val topic = TestKafka.uniqueTopic("books")
            testKafka.produceRecords(
                topic,
                "avro",
                Book(title = "Station Eleven", wordCount = 300, authorId = UUID.randomUUID().toString())
            )
            fileSystem.refresh()

            val source = "cat /dev/kafka/local/topics/$topic | grep 'Station Eleven'"

            val compiler = Compiler(session)
            val result = compiler.compile(source)

            assertThat(result.errors).isEmpty()
            val streamGraph = result.program.graph.children.first()
            assertThat(streamGraph.ref).isInstanceOf(NodeStreamSource::class.java)

            val filterGraph = streamGraph.children.first()
            assertThat(filterGraph.ref).isInstanceOf(NodeFilter::class.java)
        }

        @Test
        fun `round-trips cat with grep and redirection through unified path`() {
            val topic = TestKafka.uniqueTopic("books")
            testKafka.produceRecords(
                topic,
                "avro",
                Book(title = "Station Eleven", wordCount = 300, authorId = UUID.randomUUID().toString())
            )
            fileSystem.refresh()

            val source = "cat /dev/kafka/local/topics/$topic | grep 'Station Eleven' > /dev/kafka/local/topics/output"

            val compiler = Compiler(session)
            val result = compiler.compile(source)

            assertThat(result.errors).isEmpty()

            // Should have source → filter → sink (no auto-sink since there's a redirection)
            val streamGraph = result.program.graph.children.first()
            assertThat(streamGraph.ref).isInstanceOf(NodeStreamSource::class.java)

            val filterGraph = streamGraph.children.first()
            assertThat(filterGraph.ref).isInstanceOf(NodeFilter::class.java)

            val sinkGraph = filterGraph.children.first()
            assertThat(sinkGraph.ref).isInstanceOf(NodeSink::class.java)
            assertThat((sinkGraph.ref as NodeSink).output.path).isEqualTo("/dev/kafka/local/topics/output")
        }
    }
}
