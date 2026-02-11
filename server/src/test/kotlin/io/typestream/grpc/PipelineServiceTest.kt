package io.typestream.grpc

import io.grpc.inprocess.InProcessChannelBuilder
import io.grpc.inprocess.InProcessServerBuilder
import io.grpc.testing.GrpcCleanupRule
import io.typestream.Server
import io.typestream.config.testing.testConfig
import io.typestream.grpc.job_service.Job
import io.typestream.grpc.pipeline_service.Pipeline
import io.typestream.grpc.pipeline_service.PipelineServiceGrpc
import io.typestream.testing.TestKafka
import io.typestream.testing.TestKafkaContainer
import io.typestream.testing.model.Book
import io.typestream.testing.until
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.Rule
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.testcontainers.junit.jupiter.Testcontainers
import java.util.UUID

@Testcontainers
internal class PipelineServiceTest {
    private val dispatcher = Dispatchers.IO

    private lateinit var app: Server

    @get:Rule
    val grpcCleanupRule: GrpcCleanupRule = GrpcCleanupRule()

    companion object {
        private val testKafka = TestKafkaContainer.instance
    }

    @BeforeEach
    fun beforeEach() {
        app = Server(testConfig(testKafka), dispatcher)
    }

    private fun buildSimpleGraph(topicPath: String): Job.PipelineGraph {
        val sourceNode = Job.PipelineNode.newBuilder()
            .setId("source-1")
            .setStreamSource(
                Job.StreamSourceNode.newBuilder()
                    .setDataStream(Job.DataStreamProto.newBuilder().setPath(topicPath))
                    .setEncoding(Job.Encoding.AVRO)
            )
            .build()

        val filterNode = Job.PipelineNode.newBuilder()
            .setId("filter-1")
            .setFilter(
                Job.FilterNode.newBuilder()
                    .setByKey(false)
                    .setPredicate(Job.PredicateProto.newBuilder().setExpr("Station"))
            )
            .build()

        return Job.PipelineGraph.newBuilder()
            .addNodes(sourceNode)
            .addNodes(filterNode)
            .addEdges(Job.PipelineEdge.newBuilder().setFromId("source-1").setToId("filter-1"))
            .build()
    }

    private fun buildMetadata(
        name: String,
        version: String = "1",
        description: String = ""
    ): Pipeline.PipelineMetadata {
        return Pipeline.PipelineMetadata.newBuilder()
            .setName(name)
            .setVersion(version)
            .setDescription(description)
            .build()
    }

    @Test
    fun `validates a valid pipeline`(): Unit = runBlocking {
        val topic = TestKafka.uniqueTopic("books")

        app.use {
            testKafka.produceRecords(
                topic,
                "avro",
                Book(title = "Station Eleven", wordCount = 300, authorId = UUID.randomUUID().toString())
            )

            val serverName = InProcessServerBuilder.generateName()
            launch(dispatcher) {
                app.run(InProcessServerBuilder.forName(serverName).directExecutor())
            }

            until { requireNotNull(app.server) }

            grpcCleanupRule.register(app.server ?: return@use)

            val stub = PipelineServiceGrpc.newBlockingStub(
                grpcCleanupRule.register(InProcessChannelBuilder.forName(serverName).directExecutor().build())
            )

            val graph = buildSimpleGraph("/dev/kafka/local/topics/$topic")
            val request = Pipeline.ValidatePipelineRequest.newBuilder()
                .setMetadata(buildMetadata("test-pipeline"))
                .setGraph(graph)
                .build()

            val response = stub.validatePipeline(request)

            assertThat(response.valid).isTrue()
            assertThat(response.errorsList).isEmpty()
        }
    }

    @Test
    fun `validates pipeline with missing name`(): Unit = runBlocking {
        app.use {
            val serverName = InProcessServerBuilder.generateName()
            launch(dispatcher) {
                app.run(InProcessServerBuilder.forName(serverName).directExecutor())
            }

            until { requireNotNull(app.server) }

            grpcCleanupRule.register(app.server ?: return@use)

            val stub = PipelineServiceGrpc.newBlockingStub(
                grpcCleanupRule.register(InProcessChannelBuilder.forName(serverName).directExecutor().build())
            )

            val request = Pipeline.ValidatePipelineRequest.newBuilder()
                .setMetadata(buildMetadata(""))
                .setGraph(Job.PipelineGraph.getDefaultInstance())
                .build()

            val response = stub.validatePipeline(request)

            assertThat(response.valid).isFalse()
            assertThat(response.errorsList).anyMatch { it.contains("name") }
        }
    }

    @Test
    fun `applies a new pipeline`(): Unit = runBlocking {
        val topic = TestKafka.uniqueTopic("books")

        app.use {
            testKafka.produceRecords(
                topic,
                "avro",
                Book(title = "Station Eleven", wordCount = 300, authorId = UUID.randomUUID().toString())
            )

            val serverName = InProcessServerBuilder.generateName()
            launch(dispatcher) {
                app.run(InProcessServerBuilder.forName(serverName).directExecutor())
            }

            until { requireNotNull(app.server) }

            grpcCleanupRule.register(app.server ?: return@use)

            val stub = PipelineServiceGrpc.newBlockingStub(
                grpcCleanupRule.register(InProcessChannelBuilder.forName(serverName).directExecutor().build())
            )

            val graph = buildSimpleGraph("/dev/kafka/local/topics/$topic")
            val request = Pipeline.ApplyPipelineRequest.newBuilder()
                .setMetadata(buildMetadata("my-pipeline", "1", "Test pipeline"))
                .setGraph(graph)
                .build()

            val response = stub.applyPipeline(request)

            assertThat(response.success).isTrue()
            assertThat(response.jobId).isNotEmpty()
            assertThat(response.state).isEqualTo(Pipeline.PipelineState.CREATED)
        }
    }

    @Test
    fun `apply same pipeline twice returns unchanged`(): Unit = runBlocking {
        val topic = TestKafka.uniqueTopic("books")

        app.use {
            testKafka.produceRecords(
                topic,
                "avro",
                Book(title = "Station Eleven", wordCount = 300, authorId = UUID.randomUUID().toString())
            )

            val serverName = InProcessServerBuilder.generateName()
            launch(dispatcher) {
                app.run(InProcessServerBuilder.forName(serverName).directExecutor())
            }

            until { requireNotNull(app.server) }

            grpcCleanupRule.register(app.server ?: return@use)

            val stub = PipelineServiceGrpc.newBlockingStub(
                grpcCleanupRule.register(InProcessChannelBuilder.forName(serverName).directExecutor().build())
            )

            val graph = buildSimpleGraph("/dev/kafka/local/topics/$topic")
            val metadata = buildMetadata("idempotent-pipeline")

            // First apply
            val request1 = Pipeline.ApplyPipelineRequest.newBuilder()
                .setMetadata(metadata)
                .setGraph(graph)
                .build()
            val response1 = stub.applyPipeline(request1)
            assertThat(response1.success).isTrue()
            assertThat(response1.state).isEqualTo(Pipeline.PipelineState.CREATED)

            // Second apply with same graph
            val response2 = stub.applyPipeline(request1)
            assertThat(response2.success).isTrue()
            assertThat(response2.state).isEqualTo(Pipeline.PipelineState.UNCHANGED)
            assertThat(response2.jobId).isEqualTo(response1.jobId)
        }
    }

    @Test
    fun `lists managed pipelines`(): Unit = runBlocking {
        val topic = TestKafka.uniqueTopic("books")

        app.use {
            testKafka.produceRecords(
                topic,
                "avro",
                Book(title = "Station Eleven", wordCount = 300, authorId = UUID.randomUUID().toString())
            )

            val serverName = InProcessServerBuilder.generateName()
            launch(dispatcher) {
                app.run(InProcessServerBuilder.forName(serverName).directExecutor())
            }

            until { requireNotNull(app.server) }

            grpcCleanupRule.register(app.server ?: return@use)

            val stub = PipelineServiceGrpc.newBlockingStub(
                grpcCleanupRule.register(InProcessChannelBuilder.forName(serverName).directExecutor().build())
            )

            // Apply a pipeline first
            val graph = buildSimpleGraph("/dev/kafka/local/topics/$topic")
            val applyRequest = Pipeline.ApplyPipelineRequest.newBuilder()
                .setMetadata(buildMetadata("listed-pipeline", "2", "A listed pipeline"))
                .setGraph(graph)
                .build()
            stub.applyPipeline(applyRequest)

            // List pipelines
            val listResponse = stub.listPipelines(Pipeline.ListPipelinesRequest.getDefaultInstance())

            assertThat(listResponse.pipelinesCount).isGreaterThanOrEqualTo(1)

            val found = listResponse.pipelinesList.find { it.name == "listed-pipeline" }
            assertThat(found).isNotNull()
            assertThat(found!!.version).isEqualTo("2")
            assertThat(found.description).isEqualTo("A listed pipeline")
            assertThat(found.jobId).isNotEmpty()
        }
    }

    @Test
    fun `deletes a managed pipeline`(): Unit = runBlocking {
        val topic = TestKafka.uniqueTopic("books")

        app.use {
            testKafka.produceRecords(
                topic,
                "avro",
                Book(title = "Station Eleven", wordCount = 300, authorId = UUID.randomUUID().toString())
            )

            val serverName = InProcessServerBuilder.generateName()
            launch(dispatcher) {
                app.run(InProcessServerBuilder.forName(serverName).directExecutor())
            }

            until { requireNotNull(app.server) }

            grpcCleanupRule.register(app.server ?: return@use)

            val stub = PipelineServiceGrpc.newBlockingStub(
                grpcCleanupRule.register(InProcessChannelBuilder.forName(serverName).directExecutor().build())
            )

            // Apply a pipeline
            val graph = buildSimpleGraph("/dev/kafka/local/topics/$topic")
            val applyRequest = Pipeline.ApplyPipelineRequest.newBuilder()
                .setMetadata(buildMetadata("deletable-pipeline"))
                .setGraph(graph)
                .build()
            stub.applyPipeline(applyRequest)

            // Delete it
            val deleteRequest = Pipeline.DeletePipelineRequest.newBuilder()
                .setName("deletable-pipeline")
                .build()
            val deleteResponse = stub.deletePipeline(deleteRequest)

            assertThat(deleteResponse.success).isTrue()

            // Verify it's gone
            val listResponse = stub.listPipelines(Pipeline.ListPipelinesRequest.getDefaultInstance())
            val found = listResponse.pipelinesList.find { it.name == "deletable-pipeline" }
            assertThat(found).isNull()
        }
    }

    @Test
    fun `delete nonexistent pipeline returns error`(): Unit = runBlocking {
        app.use {
            val serverName = InProcessServerBuilder.generateName()
            launch(dispatcher) {
                app.run(InProcessServerBuilder.forName(serverName).directExecutor())
            }

            until { requireNotNull(app.server) }

            grpcCleanupRule.register(app.server ?: return@use)

            val stub = PipelineServiceGrpc.newBlockingStub(
                grpcCleanupRule.register(InProcessChannelBuilder.forName(serverName).directExecutor().build())
            )

            val deleteRequest = Pipeline.DeletePipelineRequest.newBuilder()
                .setName("nonexistent")
                .build()
            val response = stub.deletePipeline(deleteRequest)

            assertThat(response.success).isFalse()
            assertThat(response.error).contains("not found")
        }
    }
}
