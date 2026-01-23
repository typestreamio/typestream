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

    @Test
    fun `preview job can receive messages and be stopped after client disconnects`(): Unit = runBlocking {
        // This test verifies that:
        // 1. Preview jobs can stream messages to clients
        // 2. After client cancels, the job can still be manually stopped (fallback cleanup)
        // Note: Automatic cleanup on client disconnect is best-effort due to gRPC/Kotlin flow limitations
        // The TTL-based cleanup provides a reliable fallback
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

            val createResponse = blockingStub.createPreviewJob(
                Job.CreatePreviewJobRequest.newBuilder()
                    .setGraph(graph)
                    .setInspectorNodeId("inspector-node-1")
                    .build()
            )

            assertThat(createResponse.success).isTrue()
            val jobId = createResponse.jobId

            // Wait for Kafka Streams to start
            delay(2000)

            // Start streaming and cancel after receiving a message
            val latch = CountDownLatch(1)
            val receivedMessages = CopyOnWriteArrayList<Job.StreamPreviewResponse>()

            val streamRequest = Job.StreamPreviewRequest.newBuilder()
                .setJobId(jobId)
                .build()

            val call = channel.newCall(
                JobServiceGrpc.getStreamPreviewMethod(),
                io.grpc.CallOptions.DEFAULT
            )

            call.start(object : io.grpc.ClientCall.Listener<Job.StreamPreviewResponse>() {
                override fun onMessage(message: Job.StreamPreviewResponse) {
                    receivedMessages.add(message)
                    // Cancel after receiving first message (simulates browser close)
                    call.cancel("Client cancelled", null)
                    latch.countDown()
                }

                override fun onClose(status: io.grpc.Status, trailers: io.grpc.Metadata) {
                    latch.countDown()
                }
            }, io.grpc.Metadata())

            call.sendMessage(streamRequest)
            call.halfClose()
            call.request(100)

            // Wait for message and cancel
            latch.await(30, TimeUnit.SECONDS)

            // Verify we received at least one message
            assertThat(receivedMessages).isNotEmpty()

            // Manual stop should work as fallback cleanup
            // (automatic cleanup via stream cancellation is best-effort)
            val stopResponse = blockingStub.stopPreviewJob(
                Job.StopPreviewJobRequest.newBuilder().setJobId(jobId).build()
            )

            // Either automatic cleanup happened (success=false) or manual stop works (success=true)
            // Both are acceptable outcomes
            println("Stop response: success=${stopResponse.success}, error=${stopResponse.error}")

            if (stopResponse.success) {
                // Manual stop worked - verify it's actually gone now
                val stopAgain = blockingStub.stopPreviewJob(
                    Job.StopPreviewJobRequest.newBuilder().setJobId(jobId).build()
                )
                assertThat(stopAgain.success).isFalse()
            } else {
                // Automatic cleanup happened
                assertThat(stopResponse.error).contains("not found")
            }
        }
    }

    @Test
    fun `filter node filters messages based on expression`(): Unit = runBlocking {
        app.use {
            // Produce test data with varying word counts
            val testBooks = listOf(
                Book(title = "Short Book", wordCount = 100, authorId = UUID.randomUUID().toString()),
                Book(title = "Medium Book", wordCount = 250, authorId = UUID.randomUUID().toString()),
                Book(title = "Long Book", wordCount = 400, authorId = UUID.randomUUID().toString()),
                Book(title = "Very Long Book", wordCount = 500, authorId = UUID.randomUUID().toString()),
                Book(title = "Another Short", wordCount = 150, authorId = UUID.randomUUID().toString())
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

            // Build graph: StreamSource -> Filter -> Inspector
            // Filter: .word_count > 200 (should only pass Medium, Long, Very Long)
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
                        .setPredicate(Job.PredicateProto.newBuilder().setExpr(".word_count > 200"))
                )
                .build()

            val inspectorNode = Job.PipelineNode.newBuilder()
                .setId("inspector-node-1")
                .setInspector(Job.InspectorNode.newBuilder().setLabel("filtered-output"))
                .build()

            val graph = Job.PipelineGraph.newBuilder()
                .addNodes(streamSourceNode)
                .addNodes(filterNode)
                .addNodes(inspectorNode)
                .addEdges(Job.PipelineEdge.newBuilder().setFromId("source").setToId("filter"))
                .addEdges(Job.PipelineEdge.newBuilder().setFromId("filter").setToId("inspector-node-1"))
                .build()

            // Step 1: Create preview job
            val createRequest = Job.CreatePreviewJobRequest.newBuilder()
                .setGraph(graph)
                .setInspectorNodeId("inspector-node-1")
                .build()

            val createResponse = blockingStub.createPreviewJob(createRequest)

            println("CreatePreviewJob response: success=${createResponse.success}, jobId=${createResponse.jobId}, error=${createResponse.error}")

            assertThat(createResponse.success).isTrue()
            assertThat(createResponse.jobId).isNotEmpty()

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

            println("Starting streamPreview for filtered jobId=$jobId")

            asyncStub.streamPreview(streamRequest, object : StreamObserver<Job.StreamPreviewResponse> {
                override fun onNext(response: Job.StreamPreviewResponse) {
                    println("Received filtered message: value=${response.value}")
                    receivedMessages.add(response)
                    // Expect exactly 3 messages (Medium, Long, Very Long)
                    if (receivedMessages.size >= 3) {
                        latch.countDown()
                    }
                }

                override fun onError(t: Throwable) {
                    println("Stream error: ${t.message}")
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
            blockingStub.stopPreviewJob(stopRequest)

            // Assertions
            assertThat(streamError).isNull()
            assertThat(receivedMessages).isNotEmpty()

            // Should have exactly 3 messages (word_count > 200: 250, 400, 500)
            assertThat(receivedMessages.size).isEqualTo(3)

            // Verify the filtered messages contain the expected books
            val allValues = receivedMessages.map { it.value }
            assertThat(allValues.any { it.contains("Medium Book") }).isTrue()
            assertThat(allValues.any { it.contains("Long Book") }).isTrue()
            assertThat(allValues.any { it.contains("Very Long Book") }).isTrue()

            // Verify the filtered OUT messages are NOT present
            assertThat(allValues.none { it.contains("Short Book") }).isTrue()
            assertThat(allValues.none { it.contains("Another Short") }).isTrue()
        }
    }

    @Test
    fun `preview job not in list jobs response`(): Unit = runBlocking {
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

            // List jobs - preview job should NOT be in the list
            val listResponse = stub.listJobs(Job.ListJobsRequest.newBuilder().build())

            val jobIds = listResponse.jobsList.map { it.jobId }
            assertThat(jobIds).doesNotContain(jobId)

            // Cleanup
            stub.stopPreviewJob(Job.StopPreviewJobRequest.newBuilder().setJobId(jobId).build())
        }
    }
}
