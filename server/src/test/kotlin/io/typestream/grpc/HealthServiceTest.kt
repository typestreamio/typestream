package io.typestream.grpc

import io.grpc.health.v1.HealthCheckRequest
import io.grpc.health.v1.HealthCheckResponse.ServingStatus
import io.grpc.health.v1.HealthGrpc
import io.grpc.inprocess.InProcessChannelBuilder
import io.grpc.inprocess.InProcessServerBuilder
import io.grpc.testing.GrpcCleanupRule
import io.typestream.Server
import io.typestream.config.testing.testConfig
import io.typestream.grpc.job_service.Job
import io.typestream.grpc.job_service.JobServiceGrpc
import io.typestream.testing.TestKafka
import io.typestream.testing.TestKafkaContainer
import io.typestream.testing.model.Book
import io.typestream.testing.until
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.Rule
import org.junit.jupiter.api.Test
import org.testcontainers.junit.jupiter.Testcontainers
import java.util.UUID
import kotlin.time.Duration.Companion.milliseconds

@Testcontainers
internal class HealthServiceTest {
    private val dispatcher = Dispatchers.IO

    @get:Rule
    val grpcCleanupRule: GrpcCleanupRule = GrpcCleanupRule()

    companion object {
        private val testKafka = TestKafkaContainer.instance
    }

    @Test
    fun `reports SERVING when a running pipeline is healthy`(): Unit = runBlocking {
        val topic = TestKafka.uniqueTopic("books")
        // Poll fast so the monitor re-evaluates job state quickly during the test.
        val app = Server(testConfig(testKafka), dispatcher, healthPollInterval = 100.milliseconds)

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

            val channel = grpcCleanupRule.register(
                InProcessChannelBuilder.forName(serverName).directExecutor().build()
            )
            val jobStub = JobServiceGrpc.newBlockingStub(channel)
            val healthStub = HealthGrpc.newBlockingStub(channel)

            val streamSource = Job.PipelineNode.newBuilder()
                .setId("source")
                .setStreamSource(
                    Job.StreamSourceNode.newBuilder()
                        .setDataStream(Job.DataStreamProto.newBuilder().setPath("/dev/kafka/local/topics/$topic"))
                        .setEncoding(Job.Encoding.AVRO)
                )
                .build()
            val filter = Job.PipelineNode.newBuilder()
                .setId("filter")
                .setFilter(
                    Job.FilterNode.newBuilder()
                        .setByKey(false)
                        .setPredicate(Job.PredicateProto.newBuilder().setExpr("Station"))
                )
                .build()
            val graph = Job.PipelineGraph.newBuilder()
                .addNodes(streamSource)
                .addNodes(filter)
                .addEdges(Job.PipelineEdge.newBuilder().setFromId("source").setToId("filter"))
                .build()

            val response = jobStub.createJobFromGraph(
                Job.CreateJobFromGraphRequest.newBuilder().setUserId("test-user").setGraph(graph).build()
            )
            assertThat(response.success).isTrue()

            val healthRequest = HealthCheckRequest.newBuilder().setService("").build()
            until("health is SERVING") {
                require(healthStub.check(healthRequest).status == ServingStatus.SERVING)
            }

            assertThat(healthStub.check(healthRequest).status).isEqualTo(ServingStatus.SERVING)
        }
    }
}
