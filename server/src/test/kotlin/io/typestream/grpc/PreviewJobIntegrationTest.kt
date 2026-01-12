package io.typestream.grpc

import io.grpc.inprocess.InProcessChannelBuilder
import io.grpc.inprocess.InProcessServerBuilder
import io.grpc.stub.StreamObserver
import io.grpc.testing.GrpcCleanupRule
import io.typestream.Server
import io.typestream.config.testing.testConfig
import io.typestream.grpc.job_service.Job
import io.typestream.grpc.job_service.JobServiceGrpc
import io.typestream.testing.TestKafka
import io.typestream.testing.model.Book
import io.typestream.testing.until
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.Rule
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@Testcontainers
internal class PreviewJobIntegrationTest {
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
    fun `creates preview job and streams data from inspector`(): Unit = runBlocking {
        app.use {
            // Produce test data - must use supported topic name "books"
            val testBooks = listOf(
                Book(title = "Book One", wordCount = 100, authorId = UUID.randomUUID().toString()),
                Book(title = "Book Two", wordCount = 200, authorId = UUID.randomUUID().toString()),
                Book(title = "Book Three", wordCount = 300, authorId = UUID.randomUUID().toString())
            )
            testKafka.produceRecords("books", "avro", *testBooks.toTypedArray())

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
            val asyncStub = JobServiceGrpc.newStub(channel)

            // Build graph: StreamSource -> Inspector
            val streamSourceNode = Job.PipelineNode.newBuilder()
                .setId("source")
                .setStreamSource(
                    Job.StreamSourceNode.newBuilder()
                        .setDataStream(Job.DataStreamProto.newBuilder().setPath("/dev/kafka/local/topics/books"))
                        .setEncoding(Job.Encoding.AVRO)
                )
                .build()

            val inspectorNode = Job.PipelineNode.newBuilder()
                .setId("inspector-node-1")
                .setInspector(Job.InspectorNode.newBuilder().setLabel("test-inspector"))
                .build()

            val edge = Job.PipelineEdge.newBuilder()
                .setFromId("source")
                .setToId("inspector-node-1")
                .build()

            val graph = Job.PipelineGraph.newBuilder()
                .addNodes(streamSourceNode)
                .addNodes(inspectorNode)
                .addEdges(edge)
                .build()

            // Step 1: Create preview job
            val createRequest = Job.CreatePreviewJobRequest.newBuilder()
                .setGraph(graph)
                .setInspectorNodeId("inspector-node-1")
                .build()

            val createResponse = blockingStub.createPreviewJob(createRequest)

            println("CreatePreviewJob response: success=${createResponse.success}, jobId=${createResponse.jobId}, error=${createResponse.error}, inspectTopic=${createResponse.inspectTopic}")

            assertThat(createResponse.success).isTrue()
            assertThat(createResponse.jobId).isNotEmpty()
            assertThat(createResponse.inspectTopic).contains("inspect-inspector-node-1")

            val jobId = createResponse.jobId

            // Wait for Kafka Streams to start processing
            delay(2000)

            // Step 2: Stream preview data
            val receivedMessages = CopyOnWriteArrayList<Job.StreamPreviewResponse>()
            val latch = CountDownLatch(1)
            var streamError: Throwable? = null

            val streamRequest = Job.StreamPreviewRequest.newBuilder()
                .setJobId(jobId)
                .build()

            println("Starting streamPreview for jobId=$jobId")

            asyncStub.streamPreview(streamRequest, object : StreamObserver<Job.StreamPreviewResponse> {
                override fun onNext(response: Job.StreamPreviewResponse) {
                    println("Received message: key=${response.key}, value=${response.value}, ts=${response.timestamp}")
                    receivedMessages.add(response)
                    if (receivedMessages.size >= 3) {
                        latch.countDown()
                    }
                }

                override fun onError(t: Throwable) {
                    println("Stream error: ${t.message}")
                    t.printStackTrace()
                    streamError = t
                    latch.countDown()
                }

                override fun onCompleted() {
                    println("Stream completed with ${receivedMessages.size} messages")
                    latch.countDown()
                }
            })

            // Wait for messages or timeout
            val received = latch.await(30, TimeUnit.SECONDS)

            println("Latch result: received=$received, messageCount=${receivedMessages.size}, error=$streamError")

            // Step 3: Stop preview job
            val stopRequest = Job.StopPreviewJobRequest.newBuilder()
                .setJobId(jobId)
                .build()
            val stopResponse = blockingStub.stopPreviewJob(stopRequest)

            println("StopPreviewJob response: success=${stopResponse.success}, error=${stopResponse.error}")

            // Assertions
            assertThat(streamError).isNull()
            assertThat(receivedMessages).isNotEmpty()
            assertThat(receivedMessages.size).isGreaterThanOrEqualTo(3)

            // Verify message content contains book data
            val allValues = receivedMessages.map { it.value }
            assertThat(allValues.any { it.contains("Book One") || it.contains("title") }).isTrue()
        }
    }

    @Test
    fun `preview job returns error for non-existent job`(): Unit = runBlocking {
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
            val asyncStub = JobServiceGrpc.newStub(channel)

            val latch = CountDownLatch(1)
            var streamError: Throwable? = null

            val streamRequest = Job.StreamPreviewRequest.newBuilder()
                .setJobId("non-existent-job-id")
                .build()

            asyncStub.streamPreview(streamRequest, object : StreamObserver<Job.StreamPreviewResponse> {
                override fun onNext(response: Job.StreamPreviewResponse) {}

                override fun onError(t: Throwable) {
                    streamError = t
                    latch.countDown()
                }

                override fun onCompleted() {
                    latch.countDown()
                }
            })

            latch.await(10, TimeUnit.SECONDS)

            assertThat(streamError).isNotNull()
            assertThat(streamError?.message).contains("Preview job not found")
        }
    }

    @Test
    fun `stop preview job removes it from active jobs`(): Unit = runBlocking {
        app.use {
            testKafka.produceRecords(
                "books",
                "avro",
                Book(title = "Stop Test", wordCount = 100, authorId = UUID.randomUUID().toString())
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

            // Create preview job
            val streamSourceNode = Job.PipelineNode.newBuilder()
                .setId("source")
                .setStreamSource(
                    Job.StreamSourceNode.newBuilder()
                        .setDataStream(Job.DataStreamProto.newBuilder().setPath("/dev/kafka/local/topics/books"))
                        .setEncoding(Job.Encoding.AVRO)
                )
                .build()

            val inspectorNode = Job.PipelineNode.newBuilder()
                .setId("inspector-node-1")
                .setInspector(Job.InspectorNode.newBuilder().setLabel("test"))
                .build()

            val graph = Job.PipelineGraph.newBuilder()
                .addNodes(streamSourceNode)
                .addNodes(inspectorNode)
                .addEdges(Job.PipelineEdge.newBuilder().setFromId("source").setToId("inspector-node-1"))
                .build()

            val createResponse = stub.createPreviewJob(
                Job.CreatePreviewJobRequest.newBuilder()
                    .setGraph(graph)
                    .setInspectorNodeId("inspector-node-1")
                    .build()
            )

            assertThat(createResponse.success).isTrue()
            val jobId = createResponse.jobId

            // Stop the preview job
            val stopResponse = stub.stopPreviewJob(
                Job.StopPreviewJobRequest.newBuilder().setJobId(jobId).build()
            )

            assertThat(stopResponse.success).isTrue()

            // Try to stop again - should fail
            val stopAgainResponse = stub.stopPreviewJob(
                Job.StopPreviewJobRequest.newBuilder().setJobId(jobId).build()
            )

            assertThat(stopAgainResponse.success).isFalse()
            assertThat(stopAgainResponse.error).contains("not found")
        }
    }
}
