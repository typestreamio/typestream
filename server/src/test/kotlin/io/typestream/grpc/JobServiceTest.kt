package io.typestream.grpc

import io.grpc.inprocess.InProcessChannelBuilder
import io.grpc.inprocess.InProcessServerBuilder
import io.grpc.testing.GrpcCleanupRule
import io.typestream.Server
import io.typestream.config.testing.testConfig
import io.typestream.grpc.job_service.Job
import io.typestream.grpc.job_service.JobServiceGrpc
import io.typestream.testing.TestKafka
import io.typestream.testing.model.Book
import io.typestream.testing.until
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.Rule
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.util.UUID

@Testcontainers
internal class JobServiceTest {
    private val dispatcher = Dispatchers.IO

    private lateinit var app: Server

    @get:Rule
    val grpcCleanupRule: GrpcCleanupRule = GrpcCleanupRule()

    @Container
    private val testKafka = TestKafka()

    @BeforeEach
    fun beforeEach() {
        app = Server(testConfig(testKafka), dispatcher)
    }

    @Test
    fun `creates job from text source`(): Unit = runBlocking {
        app.use {
            testKafka.produceRecords(
                "books",
                "avro",
                Book(title = "Station Eleven", wordCount = 300, authorId = UUID.randomUUID().toString())
            )

            val serverName = InProcessServerBuilder.generateName()
            launch(dispatcher) {
                app.run(InProcessServerBuilder.forName(serverName).directExecutor())
            }

            until { requireNotNull(app.server) }

            grpcCleanupRule.register(app.server ?: return@use)

            val stub = JobServiceGrpc.newBlockingStub(
                grpcCleanupRule.register(InProcessChannelBuilder.forName(serverName).directExecutor().build())
            )

            val request = Job.CreateJobRequest.newBuilder()
                .setUserId("test-user")
                .setSource("cat /dev/kafka/local/topics/books | grep 'Station'")
                .build()

            val response = stub.createJob(request)

            assertThat(response.success).isTrue()
            assertThat(response.error).isEmpty()
            assertThat(response.jobId).isNotEmpty()
        }
    }

    @Test
    fun `creates job from graph`(): Unit = runBlocking {
        app.use {
            testKafka.produceRecords(
                "books",
                "avro",
                Book(title = "Station Eleven", wordCount = 300, authorId = UUID.randomUUID().toString())
            )

            val serverName = InProcessServerBuilder.generateName()
            launch(dispatcher) {
                app.run(InProcessServerBuilder.forName(serverName).directExecutor())
            }

            until { requireNotNull(app.server) }

            grpcCleanupRule.register(app.server ?: return@use)

            val stub = JobServiceGrpc.newBlockingStub(
                grpcCleanupRule.register(InProcessChannelBuilder.forName(serverName).directExecutor().build())
            )

            // Build graph: StreamSource -> Filter
            val streamSourceNode = Job.PipelineNode.newBuilder()
                .setId("source")
                .setStreamSource(
                    Job.StreamSourceNode.newBuilder()
                        .setDataStream(Job.DataStreamProto.newBuilder().setPath("/dev/kafka/local/topics/books"))
                        .setEncoding(Job.Encoding.AVRO)
                )
                .build()

            val filterNode = Job.PipelineNode.newBuilder()
                .setId("filter")
                .setFilter(
                    Job.FilterNode.newBuilder()
                        .setByKey(false)
                        .setPredicate(Job.PredicateProto.newBuilder().setExpr("Station"))
                )
                .build()

            val edge = Job.PipelineEdge.newBuilder()
                .setFromId("source")
                .setToId("filter")
                .build()

            val graph = Job.PipelineGraph.newBuilder()
                .addNodes(streamSourceNode)
                .addNodes(filterNode)
                .addEdges(edge)
                .build()

            val request = Job.CreateJobFromGraphRequest.newBuilder()
                .setUserId("test-user")
                .setGraph(graph)
                .build()

            val response = stub.createJobFromGraph(request)

            assertThat(response.success).isTrue()
            assertThat(response.error).isEmpty()
            assertThat(response.jobId).isNotEmpty()
        }
    }

    @Test
    fun `returns error for invalid graph path`(): Unit = runBlocking {
        app.use {
            val serverName = InProcessServerBuilder.generateName()
            launch(dispatcher) {
                app.run(InProcessServerBuilder.forName(serverName).directExecutor())
            }

            until { requireNotNull(app.server) }

            grpcCleanupRule.register(app.server ?: return@use)

            val stub = JobServiceGrpc.newBlockingStub(
                grpcCleanupRule.register(InProcessChannelBuilder.forName(serverName).directExecutor().build())
            )

            // Build graph with invalid path
            val streamSourceNode = Job.PipelineNode.newBuilder()
                .setId("source")
                .setStreamSource(
                    Job.StreamSourceNode.newBuilder()
                        .setDataStream(Job.DataStreamProto.newBuilder().setPath("/dev/kafka/local/topics/nonexistent"))
                        .setEncoding(Job.Encoding.AVRO)
                )
                .build()

            val graph = Job.PipelineGraph.newBuilder()
                .addNodes(streamSourceNode)
                .build()

            val request = Job.CreateJobFromGraphRequest.newBuilder()
                .setUserId("test-user")
                .setGraph(graph)
                .build()

            val response = stub.createJobFromGraph(request)

            assertThat(response.success).isFalse()
            assertThat(response.error).contains("No DataStream for path")
            assertThat(response.jobId).isEmpty()
        }
    }

    @Test
    fun `returns error for cyclic graph`(): Unit = runBlocking {
        app.use {
            testKafka.produceRecords(
                "books",
                "avro",
                Book(title = "Cycle Test", wordCount = 1, authorId = UUID.randomUUID().toString())
            )

            val serverName = InProcessServerBuilder.generateName()
            launch(dispatcher) {
                app.run(InProcessServerBuilder.forName(serverName).directExecutor())
            }

            until { requireNotNull(app.server) }

            grpcCleanupRule.register(app.server ?: return@use)

            val stub = JobServiceGrpc.newBlockingStub(
                grpcCleanupRule.register(InProcessChannelBuilder.forName(serverName).directExecutor().build())
            )

            // Build cyclic graph: source -> filter -> source
            val streamSourceNode = Job.PipelineNode.newBuilder()
                .setId("source")
                .setStreamSource(
                    Job.StreamSourceNode.newBuilder()
                        .setDataStream(Job.DataStreamProto.newBuilder().setPath("/dev/kafka/local/topics/books"))
                        .setEncoding(Job.Encoding.AVRO)
                )
                .build()

            val filterNode = Job.PipelineNode.newBuilder()
                .setId("filter")
                .setFilter(
                    Job.FilterNode.newBuilder()
                        .setByKey(false)
                        .setPredicate(Job.PredicateProto.newBuilder().setExpr("anything"))
                )
                .build()

            val graph = Job.PipelineGraph.newBuilder()
                .addNodes(streamSourceNode)
                .addNodes(filterNode)
                .addEdges(Job.PipelineEdge.newBuilder().setFromId("source").setToId("filter"))
                .addEdges(Job.PipelineEdge.newBuilder().setFromId("filter").setToId("source"))
                .build()

            val request = Job.CreateJobFromGraphRequest.newBuilder()
                .setUserId("test-user")
                .setGraph(graph)
                .build()

            val response = stub.createJobFromGraph(request)

            assertThat(response.success).isFalse()
            assertThat(response.error).contains("Cycle detected")
            assertThat(response.jobId).isEmpty()
        }
    }
}
