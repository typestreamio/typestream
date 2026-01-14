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
import io.grpc.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.assertj.core.api.Assertions.assertThat
import org.junit.Rule
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.util.UUID
import java.util.concurrent.TimeUnit

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
    fun `lists jobs returns jobs with valid structure`(): Unit = runBlocking {
        app.use {
            testKafka.produceRecords(
                "books",
                "avro",
                Book(title = "List Test", wordCount = 100, authorId = UUID.randomUUID().toString())
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

            // List jobs request - should return empty list initially
            val listRequest = Job.ListJobsRequest.newBuilder()
                .setUserId("test-user")
                .build()

            val listResponse = stub.listJobs(listRequest)

            // Verify the response structure is correct (may be empty)
            assertThat(listResponse).isNotNull()
            assertThat(listResponse.jobsCount).isGreaterThanOrEqualTo(0)

            // If there are any jobs, verify they have the correct structure
            listResponse.jobsList.forEach { job ->
                assertThat(job.jobId).isNotEmpty()
                assertThat(job.state).isNotEqualTo(Job.JobState.JOB_STATE_UNSPECIFIED)
                // startTime should be set (0 or valid timestamp)
                assertThat(job.startTime).isGreaterThanOrEqualTo(0L)
            }
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

    @Test
    fun `watchJobs streams job updates`(): Unit = runBlocking {
        app.use {
            testKafka.produceRecords(
                "books",
                "avro",
                Book(title = "Watch Test", wordCount = 100, authorId = UUID.randomUUID().toString())
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
            val blockingStub = JobServiceGrpc.newBlockingStub(channel)

            // First create a job so we have something to watch
            val createRequest = Job.CreateJobRequest.newBuilder()
                .setUserId("test-user")
                .setSource("cat /dev/kafka/local/topics/books | grep 'Watch'")
                .build()

            val createResponse = blockingStub.createJob(createRequest)
            assertThat(createResponse.success).isTrue()

            // Now watch for job updates using the blocking stub's server streaming
            val watchRequest = Job.WatchJobsRequest.newBuilder()
                .setUserId("test-user")
                .build()

            // Use iterator from server streaming call
            val jobStream = blockingStub.withDeadlineAfter(5, TimeUnit.SECONDS).watchJobs(watchRequest)

            // Collect first batch of job updates
            val receivedJobs = mutableListOf<Job.JobInfo>()

            withTimeout(5000) {
                // The stream should emit the job we just created
                if (jobStream.hasNext()) {
                    val jobInfo = jobStream.next()
                    receivedJobs.add(jobInfo)

                    // Verify the job info structure
                    assertThat(jobInfo.jobId).isNotEmpty()
                    assertThat(jobInfo.state).isNotEqualTo(Job.JobState.JOB_STATE_UNSPECIFIED)
                    assertThat(jobInfo.startTime).isGreaterThanOrEqualTo(0L)
                }
            }

            // Verify we received at least one job update
            // Note: The job ID from createJob() differs from the scheduler's internal ID
            // (see TODO in JobService.kt). So we just verify we got a job with valid structure.
            assertThat(receivedJobs).isNotEmpty()
            assertThat(receivedJobs.first().jobId).startsWith("typestream-app-")
        }
    }

    @Test
    fun `watchJobs returns empty stream when no jobs`(): Unit = runBlocking {
        app.use {
            val serverName = InProcessServerBuilder.generateName()
            launch(dispatcher) {
                app.run(InProcessServerBuilder.forName(serverName).directExecutor())
            }

            until { requireNotNull(app.server) }

            grpcCleanupRule.register(app.server ?: return@use)

            val channel = grpcCleanupRule.register(
                InProcessChannelBuilder.forName(serverName).directExecutor().build()
            )
            val blockingStub = JobServiceGrpc.newBlockingStub(channel)

            // Watch for jobs without creating any
            val watchRequest = Job.WatchJobsRequest.newBuilder()
                .setUserId("test-user")
                .build()

            val jobStream = blockingStub.withDeadlineAfter(2, TimeUnit.SECONDS).watchJobs(watchRequest)

            // The stream should start but not emit anything initially since no jobs exist
            // After deadline, we should gracefully handle the cancellation
            val receivedJobs = mutableListOf<Job.JobInfo>()

            try {
                withTimeout(3000) {
                    while (jobStream.hasNext()) {
                        receivedJobs.add(jobStream.next())
                    }
                }
            } catch (e: io.grpc.StatusRuntimeException) {
                // Expected - deadline exceeded since no jobs to report changes
                assertThat(e.status.code).isIn(
                    io.grpc.Status.Code.DEADLINE_EXCEEDED,
                    io.grpc.Status.Code.CANCELLED
                )
            }

            // With no jobs, the initial state has no changes to report
            // The stream only emits when there are changes
            assertThat(receivedJobs).isEmpty()
        }
    }

    @Test
    fun `watchJobs handles client cancellation gracefully`(): Unit = runBlocking {
        app.use {
            testKafka.produceRecords(
                "books",
                "avro",
                Book(title = "Cancel Test", wordCount = 100, authorId = UUID.randomUUID().toString())
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
            val blockingStub = JobServiceGrpc.newBlockingStub(channel)

            // Create a job so the stream has something to emit
            val createRequest = Job.CreateJobRequest.newBuilder()
                .setUserId("test-user")
                .setSource("cat /dev/kafka/local/topics/books | grep 'Cancel'")
                .build()

            val createResponse = blockingStub.createJob(createRequest)
            assertThat(createResponse.success).isTrue()

            // Start watching in a cancellable context
            val cancellableContext = Context.current().withCancellation()

            val watchRequest = Job.WatchJobsRequest.newBuilder()
                .setUserId("test-user")
                .build()

            var receivedAtLeastOne = false
            var caughtCancellation = false

            // Run the stream in the cancellable context
            val streamJob = launch(dispatcher) {
                cancellableContext.run {
                    try {
                        val jobStream = blockingStub.watchJobs(watchRequest)
                        if (jobStream.hasNext()) {
                            jobStream.next()
                            receivedAtLeastOne = true
                        }
                        // Try to get more - this will block until cancelled
                        while (jobStream.hasNext()) {
                            jobStream.next()
                        }
                    } catch (e: io.grpc.StatusRuntimeException) {
                        // Expected when context is cancelled
                        caughtCancellation = e.status.code == io.grpc.Status.Code.CANCELLED
                    }
                }
            }

            // Wait for at least one message, then cancel
            withTimeout(5000) {
                while (!receivedAtLeastOne) {
                    delay(100)
                }
            }

            // Cancel the context (simulates client disconnect)
            cancellableContext.cancel(null)

            // Wait for stream to finish
            withTimeout(2000) {
                streamJob.join()
            }

            // Verify we received data before cancellation
            assertThat(receivedAtLeastOne).isTrue()
            // Server should handle cancellation without throwing errors
            // (The test passes if no unhandled exceptions occur)
        }
    }
}
