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

    @Test
    fun `plan shows create for new pipeline`(): Unit = runBlocking {
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
            val planRequest = Pipeline.PlanPipelinesRequest.newBuilder()
                .addPipelines(
                    Pipeline.PipelinePlan.newBuilder()
                        .setMetadata(buildMetadata("new-pipeline", "1"))
                        .setGraph(graph)
                )
                .build()

            val response = stub.planPipelines(planRequest)

            val createResult = response.resultsList.find { it.name == "new-pipeline" }
            assertThat(createResult).isNotNull()
            assertThat(createResult!!.action).isEqualTo(Pipeline.PipelineAction.CREATE)
            assertThat(createResult.newVersion).isEqualTo("1")
        }
    }

    @Test
    fun `plan shows unchanged for identical pipeline`(): Unit = runBlocking {
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
            val metadata = buildMetadata("unchanged-pipeline", "1")

            // Apply first
            stub.applyPipeline(
                Pipeline.ApplyPipelineRequest.newBuilder()
                    .setMetadata(metadata)
                    .setGraph(graph)
                    .build()
            )

            // Plan with same graph
            val planRequest = Pipeline.PlanPipelinesRequest.newBuilder()
                .addPipelines(
                    Pipeline.PipelinePlan.newBuilder()
                        .setMetadata(metadata)
                        .setGraph(graph)
                )
                .build()

            val response = stub.planPipelines(planRequest)

            val unchangedResult = response.resultsList.find { it.name == "unchanged-pipeline" }
            assertThat(unchangedResult).isNotNull()
            assertThat(unchangedResult!!.action).isEqualTo(Pipeline.PipelineAction.UNCHANGED_ACTION)
        }
    }

    @Test
    fun `plan shows update for modified pipeline`(): Unit = runBlocking {
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

            // Apply first
            stub.applyPipeline(
                Pipeline.ApplyPipelineRequest.newBuilder()
                    .setMetadata(buildMetadata("update-pipeline", "1"))
                    .setGraph(graph)
                    .build()
            )

            // Build a different graph (different predicate)
            val modifiedFilterNode = Job.PipelineNode.newBuilder()
                .setId("filter-1")
                .setFilter(
                    Job.FilterNode.newBuilder()
                        .setByKey(false)
                        .setPredicate(Job.PredicateProto.newBuilder().setExpr("Different"))
                )
                .build()
            val modifiedGraph = Job.PipelineGraph.newBuilder()
                .addNodes(graph.getNodes(0)) // same source
                .addNodes(modifiedFilterNode)
                .addEdges(Job.PipelineEdge.newBuilder().setFromId("source-1").setToId("filter-1"))
                .build()

            // Plan with modified graph
            val planRequest = Pipeline.PlanPipelinesRequest.newBuilder()
                .addPipelines(
                    Pipeline.PipelinePlan.newBuilder()
                        .setMetadata(buildMetadata("update-pipeline", "2"))
                        .setGraph(modifiedGraph)
                )
                .build()

            val response = stub.planPipelines(planRequest)

            val updateResult = response.resultsList.find { it.name == "update-pipeline" }
            assertThat(updateResult).isNotNull()
            assertThat(updateResult!!.action).isEqualTo(Pipeline.PipelineAction.UPDATE)
            assertThat(updateResult.currentVersion).isEqualTo("1")
            assertThat(updateResult.newVersion).isEqualTo("2")
        }
    }

    @Test
    fun `plan shows delete for removed pipeline`(): Unit = runBlocking {
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

            // Apply a pipeline
            stub.applyPipeline(
                Pipeline.ApplyPipelineRequest.newBuilder()
                    .setMetadata(buildMetadata("existing-pipeline", "1"))
                    .setGraph(graph)
                    .build()
            )

            // Plan with empty list (no pipelines desired)
            val planRequest = Pipeline.PlanPipelinesRequest.newBuilder().build()

            val response = stub.planPipelines(planRequest)

            val deleteResult = response.resultsList.find { it.name == "existing-pipeline" }
            assertThat(deleteResult).isNotNull()
            assertThat(deleteResult!!.action).isEqualTo(Pipeline.PipelineAction.DELETE)
            assertThat(deleteResult.currentVersion).isEqualTo("1")
        }
    }
}
