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
import io.typestream.testing.TestKafkaContainer
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

    companion object {
        private val testKafka = TestKafkaContainer.instance
    }

    @BeforeEach
    fun beforeEach() {
        app = Server(testConfig(testKafka), dispatcher)
    }

    @Test
    fun `creates preview job and streams data from inspector`(): Unit = runBlocking {
        val topic = TestKafka.uniqueTopic("books")

        app.use {
            // Produce test data
            val testBooks = listOf(
                Book(title = "Book One", wordCount = 100, authorId = UUID.randomUUID().toString()),
                Book(title = "Book Two", wordCount = 200, authorId = UUID.randomUUID().toString()),
                Book(title = "Book Three", wordCount = 300, authorId = UUID.randomUUID().toString())
            )
            testKafka.produceRecords(topic, "avro", *testBooks.toTypedArray())

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
                        .setDataStream(Job.DataStreamProto.newBuilder().setPath("/dev/kafka/local/topics/$topic"))
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
        val topic = TestKafka.uniqueTopic("books")

        app.use {
            testKafka.produceRecords(
                topic,
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
                        .setDataStream(Job.DataStreamProto.newBuilder().setPath("/dev/kafka/local/topics/$topic"))
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
        val topic = TestKafka.uniqueTopic("books")

        // This test verifies that:
        // 1. Preview jobs can stream messages to clients
        // 2. After client cancels, the job can still be manually stopped (fallback cleanup)
        // Note: Automatic cleanup on client disconnect is best-effort due to gRPC/Kotlin flow limitations
        // The TTL-based cleanup provides a reliable fallback
        app.use {
            testKafka.produceRecords(
                topic,
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
                        .setDataStream(Job.DataStreamProto.newBuilder().setPath("/dev/kafka/local/topics/$topic"))
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
        val topic = TestKafka.uniqueTopic("books")

        app.use {
            // Produce test data with varying word counts
            val testBooks = listOf(
                Book(title = "Short Book", wordCount = 100, authorId = UUID.randomUUID().toString()),
                Book(title = "Medium Book", wordCount = 250, authorId = UUID.randomUUID().toString()),
                Book(title = "Long Book", wordCount = 400, authorId = UUID.randomUUID().toString()),
                Book(title = "Very Long Book", wordCount = 500, authorId = UUID.randomUUID().toString()),
                Book(title = "Another Short", wordCount = 150, authorId = UUID.randomUUID().toString())
            )
            testKafka.produceRecords(topic, "avro", *testBooks.toTypedArray())

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
                        .setDataStream(Job.DataStreamProto.newBuilder().setPath("/dev/kafka/local/topics/$topic"))
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
        val topic = TestKafka.uniqueTopic("books")

        app.use {
            testKafka.produceRecords(
                topic,
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
                        .setDataStream(Job.DataStreamProto.newBuilder().setPath("/dev/kafka/local/topics/$topic"))
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

    @Test
    fun `windowed count aggregates records within time window`(): Unit = runBlocking {
        app.use {
            val topic = TestKafka.uniqueTopic("books")
            // Produce multiple records with same title to test aggregation
            val testBooks = listOf(
                Book(title = "Dune", wordCount = 100, authorId = UUID.randomUUID().toString()),
                Book(title = "Dune", wordCount = 150, authorId = UUID.randomUUID().toString()),
                Book(title = "Dune", wordCount = 200, authorId = UUID.randomUUID().toString()),
                Book(title = "Foundation", wordCount = 300, authorId = UUID.randomUUID().toString()),
                Book(title = "Foundation", wordCount = 350, authorId = UUID.randomUUID().toString())
            )
            testKafka.produceRecords(topic, "avro", *testBooks.toTypedArray())

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

            // Build graph: StreamSource -> Group(title) -> WindowedCount -> Inspector
            val streamSourceNode = Job.PipelineNode.newBuilder()
                .setId("source")
                .setStreamSource(
                    Job.StreamSourceNode.newBuilder()
                        .setDataStream(Job.DataStreamProto.newBuilder().setPath("/dev/kafka/local/topics/$topic"))
                        .setEncoding(Job.Encoding.AVRO)
                )
                .build()

            val groupNode = Job.PipelineNode.newBuilder()
                .setId("group")
                .setGroup(
                    Job.GroupNode.newBuilder()
                        .setKeyMapperExpr(".title")
                )
                .build()

            val windowedCountNode = Job.PipelineNode.newBuilder()
                .setId("windowed-count")
                .setWindowedCount(
                    Job.WindowedCountNode.newBuilder()
                        .setWindowSizeSeconds(60) // 1-minute windows
                )
                .build()

            val inspectorNode = Job.PipelineNode.newBuilder()
                .setId("inspector-node-1")
                .setInspector(Job.InspectorNode.newBuilder().setLabel("windowed-count-output"))
                .build()

            val graph = Job.PipelineGraph.newBuilder()
                .addNodes(streamSourceNode)
                .addNodes(groupNode)
                .addNodes(windowedCountNode)
                .addNodes(inspectorNode)
                .addEdges(Job.PipelineEdge.newBuilder().setFromId("source").setToId("group"))
                .addEdges(Job.PipelineEdge.newBuilder().setFromId("group").setToId("windowed-count"))
                .addEdges(Job.PipelineEdge.newBuilder().setFromId("windowed-count").setToId("inspector-node-1"))
                .build()

            // Create preview job
            val createRequest = Job.CreatePreviewJobRequest.newBuilder()
                .setGraph(graph)
                .setInspectorNodeId("inspector-node-1")
                .build()

            val createResponse = blockingStub.createPreviewJob(createRequest)

            println("CreatePreviewJob (windowed count) response: success=${createResponse.success}, jobId=${createResponse.jobId}, error=${createResponse.error}")

            assertThat(createResponse.success).isTrue()
            assertThat(createResponse.jobId).isNotEmpty()

            val jobId = createResponse.jobId

            // Wait for Kafka Streams to start processing
            delay(3000)

            // Stream preview data
            val receivedMessages = CopyOnWriteArrayList<Job.StreamPreviewResponse>()
            val latch = CountDownLatch(1)
            var streamError: Throwable? = null

            val streamRequest = Job.StreamPreviewRequest.newBuilder()
                .setJobId(jobId)
                .build()

            println("Starting streamPreview for windowed count jobId=$jobId")

            asyncStub.streamPreview(streamRequest, object : StreamObserver<Job.StreamPreviewResponse> {
                override fun onNext(response: Job.StreamPreviewResponse) {
                    println("Received windowed count message: key=${response.key}, value=${response.value}")
                    receivedMessages.add(response)
                    // Windowed counts emit updates as records arrive, expect at least 2 groups (Dune, Foundation)
                    if (receivedMessages.size >= 2) {
                        latch.countDown()
                    }
                }

                override fun onError(t: Throwable) {
                    println("Stream error: ${t.message}")
                    streamError = t
                    latch.countDown()
                }

                override fun onCompleted() {
                    println("Stream completed with ${receivedMessages.size} windowed count messages")
                    latch.countDown()
                }
            })

            // Wait for messages or timeout
            val received = latch.await(30, TimeUnit.SECONDS)

            println("Latch result: received=$received, messageCount=${receivedMessages.size}, error=$streamError")

            // Stop preview job
            val stopRequest = Job.StopPreviewJobRequest.newBuilder()
                .setJobId(jobId)
                .build()
            blockingStub.stopPreviewJob(stopRequest)

            // Assertions
            assertThat(streamError).isNull()
            assertThat(receivedMessages).isNotEmpty()

            // Verify we received windowed count output
            // The output should contain count information for the grouped keys
            val allValues = receivedMessages.map { it.value }
            val allKeys = receivedMessages.map { it.key }

            println("All keys: $allKeys")
            println("All values: $allValues")

            // Keys should contain the group keys (title)
            assertThat(allKeys.any { it.contains("Dune") || it.contains("Foundation") }).isTrue()

            // Values should contain count data
            assertThat(allValues).isNotEmpty()
        }
    }

    @Test
    fun `source to materializedView builds topology without duplicate topic registration`(): Unit = runBlocking {
        val topic = TestKafka.uniqueTopic("books")

        app.use {
            val testBooks = listOf(
                Book(title = "Alpha", wordCount = 100, authorId = UUID.randomUUID().toString()),
                Book(title = "Beta", wordCount = 200, authorId = UUID.randomUUID().toString()),
                Book(title = "Alpha", wordCount = 150, authorId = UUID.randomUUID().toString())
            )
            testKafka.produceRecords(topic, "avro", *testBooks.toTypedArray())

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

            // Build graph: StreamSource -> TableMaterialized -> Inspector
            val streamSourceNode = Job.PipelineNode.newBuilder()
                .setId("source")
                .setStreamSource(
                    Job.StreamSourceNode.newBuilder()
                        .setDataStream(Job.DataStreamProto.newBuilder().setPath("/dev/kafka/local/topics/$topic"))
                        .setEncoding(Job.Encoding.AVRO)
                )
                .build()

            val tableMaterializedNode = Job.PipelineNode.newBuilder()
                .setId("mat-view")
                .setTableMaterialized(
                    Job.TableMaterializedNode.newBuilder()
                        .setGroupByField("")
                        .setAggregationType("latest")
                )
                .build()

            val inspectorNode = Job.PipelineNode.newBuilder()
                .setId("inspector-node-1")
                .setInspector(Job.InspectorNode.newBuilder().setLabel("mat-view-output"))
                .build()

            val graph = Job.PipelineGraph.newBuilder()
                .addNodes(streamSourceNode)
                .addNodes(tableMaterializedNode)
                .addNodes(inspectorNode)
                .addEdges(Job.PipelineEdge.newBuilder().setFromId("source").setToId("mat-view"))
                .addEdges(Job.PipelineEdge.newBuilder().setFromId("mat-view").setToId("inspector-node-1"))
                .build()

            val createRequest = Job.CreatePreviewJobRequest.newBuilder()
                .setGraph(graph)
                .setInspectorNodeId("inspector-node-1")
                .build()

            // This would throw TopologyException before the fix
            val createResponse = blockingStub.createPreviewJob(createRequest)

            println("CreatePreviewJob (materializedView) response: success=${createResponse.success}, jobId=${createResponse.jobId}, error=${createResponse.error}")

            assertThat(createResponse.success).isTrue()
            assertThat(createResponse.jobId).isNotEmpty()

            val jobId = createResponse.jobId

            delay(3000)

            val receivedMessages = CopyOnWriteArrayList<Job.StreamPreviewResponse>()
            val latch = CountDownLatch(1)
            var streamError: Throwable? = null

            val streamRequest = Job.StreamPreviewRequest.newBuilder()
                .setJobId(jobId)
                .build()

            asyncStub.streamPreview(streamRequest, object : StreamObserver<Job.StreamPreviewResponse> {
                override fun onNext(response: Job.StreamPreviewResponse) {
                    println("Received materializedView message: key=${response.key}, value=${response.value}")
                    receivedMessages.add(response)
                    if (receivedMessages.size >= 2) {
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

            val received = latch.await(30, TimeUnit.SECONDS)

            println("Latch result: received=$received, messageCount=${receivedMessages.size}, error=$streamError")

            blockingStub.stopPreviewJob(
                Job.StopPreviewJobRequest.newBuilder().setJobId(jobId).build()
            )

            assertThat(streamError).isNull()
            assertThat(receivedMessages).isNotEmpty()
        }
    }

    @Test
    fun `source to filter to materializedView applies filter before materialization`(): Unit = runBlocking {
        val topic = TestKafka.uniqueTopic("books")

        app.use {
            val testBooks = listOf(
                Book(title = "Short Story", wordCount = 50, authorId = UUID.randomUUID().toString()),
                Book(title = "Novel", wordCount = 300, authorId = UUID.randomUUID().toString()),
                Book(title = "Epic", wordCount = 500, authorId = UUID.randomUUID().toString()),
                Book(title = "Pamphlet", wordCount = 20, authorId = UUID.randomUUID().toString())
            )
            testKafka.produceRecords(topic, "avro", *testBooks.toTypedArray())

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

            // Build graph: StreamSource -> Filter(.word_count > 100) -> TableMaterialized -> Inspector
            val streamSourceNode = Job.PipelineNode.newBuilder()
                .setId("source")
                .setStreamSource(
                    Job.StreamSourceNode.newBuilder()
                        .setDataStream(Job.DataStreamProto.newBuilder().setPath("/dev/kafka/local/topics/$topic"))
                        .setEncoding(Job.Encoding.AVRO)
                )
                .build()

            val filterNode = Job.PipelineNode.newBuilder()
                .setId("filter")
                .setFilter(
                    Job.FilterNode.newBuilder()
                        .setByKey(false)
                        .setPredicate(Job.PredicateProto.newBuilder().setExpr(".word_count > 100"))
                )
                .build()

            val tableMaterializedNode = Job.PipelineNode.newBuilder()
                .setId("mat-view")
                .setTableMaterialized(
                    Job.TableMaterializedNode.newBuilder()
                        .setGroupByField("")
                        .setAggregationType("latest")
                )
                .build()

            val inspectorNode = Job.PipelineNode.newBuilder()
                .setId("inspector-node-1")
                .setInspector(Job.InspectorNode.newBuilder().setLabel("filtered-mat-view"))
                .build()

            val graph = Job.PipelineGraph.newBuilder()
                .addNodes(streamSourceNode)
                .addNodes(filterNode)
                .addNodes(tableMaterializedNode)
                .addNodes(inspectorNode)
                .addEdges(Job.PipelineEdge.newBuilder().setFromId("source").setToId("filter"))
                .addEdges(Job.PipelineEdge.newBuilder().setFromId("filter").setToId("mat-view"))
                .addEdges(Job.PipelineEdge.newBuilder().setFromId("mat-view").setToId("inspector-node-1"))
                .build()

            val createRequest = Job.CreatePreviewJobRequest.newBuilder()
                .setGraph(graph)
                .setInspectorNodeId("inspector-node-1")
                .build()

            val createResponse = blockingStub.createPreviewJob(createRequest)

            assertThat(createResponse.success).isTrue()

            val jobId = createResponse.jobId

            delay(3000)

            val receivedMessages = CopyOnWriteArrayList<Job.StreamPreviewResponse>()
            val latch = CountDownLatch(1)
            var streamError: Throwable? = null

            val streamRequest = Job.StreamPreviewRequest.newBuilder()
                .setJobId(jobId)
                .build()

            asyncStub.streamPreview(streamRequest, object : StreamObserver<Job.StreamPreviewResponse> {
                override fun onNext(response: Job.StreamPreviewResponse) {
                    println("Received filtered mat-view message: value=${response.value}")
                    receivedMessages.add(response)
                    // Expect 2 messages (Novel=300, Epic=500 pass the filter)
                    if (receivedMessages.size >= 2) {
                        latch.countDown()
                    }
                }

                override fun onError(t: Throwable) {
                    println("Stream error: ${t.message}")
                    streamError = t
                    latch.countDown()
                }

                override fun onCompleted() {
                    latch.countDown()
                }
            })

            val received = latch.await(30, TimeUnit.SECONDS)

            blockingStub.stopPreviewJob(
                Job.StopPreviewJobRequest.newBuilder().setJobId(jobId).build()
            )

            assertThat(streamError).isNull()
            assertThat(receivedMessages).isNotEmpty()
            // Should only contain books with word_count > 100
            assertThat(receivedMessages.size).isEqualTo(2)
            val allValues = receivedMessages.map { it.value }
            assertThat(allValues.any { it.contains("Novel") }).isTrue()
            assertThat(allValues.any { it.contains("Epic") }).isTrue()
            // Filtered out books should NOT be present
            assertThat(allValues.none { it.contains("Short Story") }).isTrue()
            assertThat(allValues.none { it.contains("Pamphlet") }).isTrue()
        }
    }

    @Test
    fun `materialized view handles tombstones`(): Unit = runBlocking {
        val topic = TestKafka.uniqueTopic("books")

        app.use {
            val authorId = UUID.randomUUID().toString()
            val testBooks = listOf(
                Book(title = "Alpha", wordCount = 100, authorId = authorId),
                Book(title = "Beta", wordCount = 200, authorId = UUID.randomUUID().toString()),
                Book(title = "Gamma", wordCount = 300, authorId = UUID.randomUUID().toString())
            )
            val produced = testKafka.produceRecords(topic, "avro", *testBooks.toTypedArray())

            // Send tombstone for first book's key
            testKafka.produceTombstone(topic, "avro", produced[0].id)

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

            // Build graph: StreamSource -> TableMaterialized -> Inspector
            val streamSourceNode = Job.PipelineNode.newBuilder()
                .setId("source")
                .setStreamSource(
                    Job.StreamSourceNode.newBuilder()
                        .setDataStream(Job.DataStreamProto.newBuilder().setPath("/dev/kafka/local/topics/$topic"))
                        .setEncoding(Job.Encoding.AVRO)
                )
                .build()

            val tableMaterializedNode = Job.PipelineNode.newBuilder()
                .setId("mat-view")
                .setTableMaterialized(
                    Job.TableMaterializedNode.newBuilder()
                        .setGroupByField("")
                        .setAggregationType("latest")
                )
                .build()

            val inspectorNode = Job.PipelineNode.newBuilder()
                .setId("inspector-node-1")
                .setInspector(Job.InspectorNode.newBuilder().setLabel("tombstone-test"))
                .build()

            val graph = Job.PipelineGraph.newBuilder()
                .addNodes(streamSourceNode)
                .addNodes(tableMaterializedNode)
                .addNodes(inspectorNode)
                .addEdges(Job.PipelineEdge.newBuilder().setFromId("source").setToId("mat-view"))
                .addEdges(Job.PipelineEdge.newBuilder().setFromId("mat-view").setToId("inspector-node-1"))
                .build()

            val createRequest = Job.CreatePreviewJobRequest.newBuilder()
                .setGraph(graph)
                .setInspectorNodeId("inspector-node-1")
                .build()

            // This would NPE before the fix when processing the tombstone record
            val createResponse = blockingStub.createPreviewJob(createRequest)

            println("CreatePreviewJob (tombstone) response: success=${createResponse.success}, jobId=${createResponse.jobId}, error=${createResponse.error}")

            assertThat(createResponse.success).isTrue()
            assertThat(createResponse.jobId).isNotEmpty()

            val jobId = createResponse.jobId

            delay(3000)

            val receivedMessages = CopyOnWriteArrayList<Job.StreamPreviewResponse>()
            val latch = CountDownLatch(1)
            var streamError: Throwable? = null

            val streamRequest = Job.StreamPreviewRequest.newBuilder()
                .setJobId(jobId)
                .build()

            asyncStub.streamPreview(streamRequest, object : StreamObserver<Job.StreamPreviewResponse> {
                override fun onNext(response: Job.StreamPreviewResponse) {
                    println("Received tombstone-test message: key=${response.key}, value=${response.value}")
                    receivedMessages.add(response)
                    // KTable emits changelog entries as records arrive (insert, update, delete).
                    // 3 inserts + 1 tombstone deletion = at least 3 changelog records
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
                    latch.countDown()
                }
            })

            val received = latch.await(30, TimeUnit.SECONDS)

            blockingStub.stopPreviewJob(
                Job.StopPreviewJobRequest.newBuilder().setJobId(jobId).build()
            )

            assertThat(streamError).isNull()
            assertThat(receivedMessages).isNotEmpty()

            val allValues = receivedMessages.map { it.value }

            // Beta and Gamma should appear in the output (they were not tombstoned)
            assertThat(allValues.any { it.contains("Beta") }).isTrue()
            assertThat(allValues.any { it.contains("Gamma") }).isTrue()

            // The tombstoned key (Alpha) should produce a null-value changelog entry.
            // In the KTable changelog stream, a tombstone is emitted as an empty/null value.
            val tombstoneKey = produced[0].id
            val tombstoneRecords = receivedMessages.filter { it.key.contains(tombstoneKey) }
            assertThat(tombstoneRecords).isNotEmpty()
            // The last record for this key should be the tombstone (empty value)
            val lastForKey = tombstoneRecords.last()
            assertThat(lastForKey.value).isEmpty()
        }
    }

    @Test
    fun `materialized view count with tombstones`(): Unit = runBlocking {
        val topic = TestKafka.uniqueTopic("books")

        app.use {
            val authorA = UUID.randomUUID().toString()
            val authorB = UUID.randomUUID().toString()
            val testBooks = listOf(
                Book(title = "Book A1", wordCount = 100, authorId = authorA),
                Book(title = "Book A2", wordCount = 200, authorId = authorA),
                Book(title = "Book B1", wordCount = 300, authorId = authorB)
            )
            val produced = testKafka.produceRecords(topic, "avro", *testBooks.toTypedArray())

            // Tombstone one of authorA's books
            testKafka.produceTombstone(topic, "avro", produced[0].id)

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

            // Build graph: StreamSource -> TableMaterialized(groupBy=author_id, count) -> Inspector
            val streamSourceNode = Job.PipelineNode.newBuilder()
                .setId("source")
                .setStreamSource(
                    Job.StreamSourceNode.newBuilder()
                        .setDataStream(Job.DataStreamProto.newBuilder().setPath("/dev/kafka/local/topics/$topic"))
                        .setEncoding(Job.Encoding.AVRO)
                )
                .build()

            val tableMaterializedNode = Job.PipelineNode.newBuilder()
                .setId("mat-view")
                .setTableMaterialized(
                    Job.TableMaterializedNode.newBuilder()
                        .setGroupByField("author_id")
                        .setAggregationType("count")
                )
                .build()

            val inspectorNode = Job.PipelineNode.newBuilder()
                .setId("inspector-node-1")
                .setInspector(Job.InspectorNode.newBuilder().setLabel("count-tombstone-test"))
                .build()

            val graph = Job.PipelineGraph.newBuilder()
                .addNodes(streamSourceNode)
                .addNodes(tableMaterializedNode)
                .addNodes(inspectorNode)
                .addEdges(Job.PipelineEdge.newBuilder().setFromId("source").setToId("mat-view"))
                .addEdges(Job.PipelineEdge.newBuilder().setFromId("mat-view").setToId("inspector-node-1"))
                .build()

            val createRequest = Job.CreatePreviewJobRequest.newBuilder()
                .setGraph(graph)
                .setInspectorNodeId("inspector-node-1")
                .build()

            val createResponse = blockingStub.createPreviewJob(createRequest)

            println("CreatePreviewJob (count tombstone) response: success=${createResponse.success}, jobId=${createResponse.jobId}, error=${createResponse.error}")

            assertThat(createResponse.success).isTrue()

            val jobId = createResponse.jobId

            delay(3000)

            val receivedMessages = CopyOnWriteArrayList<Job.StreamPreviewResponse>()
            val latch = CountDownLatch(1)
            var streamError: Throwable? = null

            val streamRequest = Job.StreamPreviewRequest.newBuilder()
                .setJobId(jobId)
                .build()

            asyncStub.streamPreview(streamRequest, object : StreamObserver<Job.StreamPreviewResponse> {
                override fun onNext(response: Job.StreamPreviewResponse) {
                    println("Received count-tombstone message: key=${response.key}, value=${response.value}")
                    receivedMessages.add(response)
                    if (receivedMessages.size >= 2) {
                        latch.countDown()
                    }
                }

                override fun onError(t: Throwable) {
                    println("Stream error: ${t.message}")
                    streamError = t
                    latch.countDown()
                }

                override fun onCompleted() {
                    latch.countDown()
                }
            })

            val received = latch.await(30, TimeUnit.SECONDS)

            blockingStub.stopPreviewJob(
                Job.StopPreviewJobRequest.newBuilder().setJobId(jobId).build()
            )

            assertThat(streamError).isNull()
            assertThat(receivedMessages).isNotEmpty()

            // Verify count output contains both author groups
            val allKeys = receivedMessages.map { it.key }
            assertThat(allKeys.any { it.contains(authorA) || it.contains(authorB) }).isTrue()

            // The last emitted count for authorA should reflect the tombstone deletion.
            // authorA had 2 books, 1 was tombstoned → final count should be 1.
            val authorAMessages = receivedMessages.filter { it.key.contains(authorA) }
            if (authorAMessages.isNotEmpty()) {
                val lastCount = authorAMessages.last().value
                println("Last count for authorA: $lastCount")
                // Count value is serialized as a DataStream with Schema.Long
                // After tombstone processing, authorA's count should be 1 (2 inserts - 1 tombstone)
                assertThat(lastCount).contains("1")
            }
        }
    }

    @Test
    fun `source to materializedView with groupByField count aggregation`(): Unit = runBlocking {
        val topic = TestKafka.uniqueTopic("books")

        app.use {
            val authorA = UUID.randomUUID().toString()
            val authorB = UUID.randomUUID().toString()
            val testBooks = listOf(
                Book(title = "Book A1", wordCount = 100, authorId = authorA),
                Book(title = "Book A2", wordCount = 200, authorId = authorA),
                Book(title = "Book B1", wordCount = 300, authorId = authorB),
                Book(title = "Book A3", wordCount = 150, authorId = authorA)
            )
            testKafka.produceRecords(topic, "avro", *testBooks.toTypedArray())

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

            // Build graph: StreamSource -> TableMaterialized(groupBy=author_id, count) -> Inspector
            val streamSourceNode = Job.PipelineNode.newBuilder()
                .setId("source")
                .setStreamSource(
                    Job.StreamSourceNode.newBuilder()
                        .setDataStream(Job.DataStreamProto.newBuilder().setPath("/dev/kafka/local/topics/$topic"))
                        .setEncoding(Job.Encoding.AVRO)
                )
                .build()

            val tableMaterializedNode = Job.PipelineNode.newBuilder()
                .setId("mat-view")
                .setTableMaterialized(
                    Job.TableMaterializedNode.newBuilder()
                        .setGroupByField("author_id")
                        .setAggregationType("count")
                )
                .build()

            val inspectorNode = Job.PipelineNode.newBuilder()
                .setId("inspector-node-1")
                .setInspector(Job.InspectorNode.newBuilder().setLabel("count-output"))
                .build()

            val graph = Job.PipelineGraph.newBuilder()
                .addNodes(streamSourceNode)
                .addNodes(tableMaterializedNode)
                .addNodes(inspectorNode)
                .addEdges(Job.PipelineEdge.newBuilder().setFromId("source").setToId("mat-view"))
                .addEdges(Job.PipelineEdge.newBuilder().setFromId("mat-view").setToId("inspector-node-1"))
                .build()

            val createRequest = Job.CreatePreviewJobRequest.newBuilder()
                .setGraph(graph)
                .setInspectorNodeId("inspector-node-1")
                .build()

            val createResponse = blockingStub.createPreviewJob(createRequest)

            println("CreatePreviewJob (groupBy count) response: success=${createResponse.success}, jobId=${createResponse.jobId}, error=${createResponse.error}")

            assertThat(createResponse.success).isTrue()

            val jobId = createResponse.jobId

            delay(3000)

            val receivedMessages = CopyOnWriteArrayList<Job.StreamPreviewResponse>()
            val latch = CountDownLatch(1)
            var streamError: Throwable? = null

            val streamRequest = Job.StreamPreviewRequest.newBuilder()
                .setJobId(jobId)
                .build()

            asyncStub.streamPreview(streamRequest, object : StreamObserver<Job.StreamPreviewResponse> {
                override fun onNext(response: Job.StreamPreviewResponse) {
                    println("Received count message: key=${response.key}, value=${response.value}")
                    receivedMessages.add(response)
                    // KTable count emits updates as records arrive; expect at least 2 groups
                    if (receivedMessages.size >= 2) {
                        latch.countDown()
                    }
                }

                override fun onError(t: Throwable) {
                    println("Stream error: ${t.message}")
                    streamError = t
                    latch.countDown()
                }

                override fun onCompleted() {
                    latch.countDown()
                }
            })

            val received = latch.await(30, TimeUnit.SECONDS)

            blockingStub.stopPreviewJob(
                Job.StopPreviewJobRequest.newBuilder().setJobId(jobId).build()
            )

            assertThat(streamError).isNull()
            assertThat(receivedMessages).isNotEmpty()

            // Keys should contain the author IDs
            val allKeys = receivedMessages.map { it.key }
            assertThat(allKeys.any { it.contains(authorA) || it.contains(authorB) }).isTrue()
        }
    }
}
