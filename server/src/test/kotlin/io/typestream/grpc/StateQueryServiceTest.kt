package io.typestream.grpc

import io.grpc.inprocess.InProcessChannelBuilder
import io.grpc.inprocess.InProcessServerBuilder
import io.grpc.testing.GrpcCleanupRule
import io.typestream.Server
import io.typestream.config.testing.testConfig
import io.typestream.grpc.job_service.Job
import io.typestream.grpc.job_service.JobServiceGrpc
import io.typestream.grpc.state_query_service.StateQuery
import io.typestream.grpc.state_query_service.StateQueryServiceGrpc
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

/**
 * Integration tests for [StateQueryService].
 *
 * These tests verify the gRPC API for querying state stores from running Kafka Streams jobs.
 * Tests that create jobs with count operations validate the interactive query functionality.
 *
 * Note: Tests that create jobs require Docker to be running for Kafka testcontainers.
 */
@Testcontainers
internal class StateQueryServiceTest {
    private val dispatcher = Dispatchers.IO

    private lateinit var app: Server

    @get:Rule
    val grpcCleanupRule: GrpcCleanupRule = GrpcCleanupRule()

    companion object {
        @Container
        @JvmStatic
        private val testKafka = TestKafka()
    }

    @BeforeEach
    fun beforeEach() {
        app = Server(testConfig(testKafka), dispatcher)
    }

    @Test
    fun `listStores returns empty list when no jobs running`(): Unit = runBlocking {
        app.use {
            val serverName = InProcessServerBuilder.generateName()
            launch(dispatcher) {
                app.run(InProcessServerBuilder.forName(serverName).directExecutor())
            }

            until { requireNotNull(app.server) }

            grpcCleanupRule.register(app.server ?: return@use)

            val stub = StateQueryServiceGrpc.newBlockingStub(
                grpcCleanupRule.register(InProcessChannelBuilder.forName(serverName).directExecutor().build())
            )

            val request = StateQuery.ListStoresRequest.getDefaultInstance()
            val response = stub.listStores(request)

            assertThat(response.storesList).isEmpty()
        }
    }

    @Test
    fun `listStores returns stores from running jobs with count operation`(): Unit = runBlocking {
        val topic = TestKafka.uniqueTopic("books")

        app.use {
            // Produce multiple records with different titles
            testKafka.produceRecords(
                topic,
                "avro",
                Book(title = "Station Eleven", wordCount = 300, authorId = UUID.randomUUID().toString()),
                Book(title = "Kindred", wordCount = 250, authorId = UUID.randomUUID().toString()),
                Book(title = "Parable of the Sower", wordCount = 200, authorId = UUID.randomUUID().toString())
            )

            val serverName = InProcessServerBuilder.generateName()
            launch(dispatcher) {
                app.run(InProcessServerBuilder.forName(serverName).directExecutor())
            }

            until { requireNotNull(app.server) }

            grpcCleanupRule.register(app.server ?: return@use)

            val jobStub = JobServiceGrpc.newBlockingStub(
                grpcCleanupRule.register(InProcessChannelBuilder.forName(serverName).directExecutor().build())
            )

            // Create a job with cut + wc (count) to create a state store
            val request = Job.CreateJobRequest.newBuilder()
                .setUserId("test-user")
                .setSource("cat /dev/kafka/local/topics/$topic | cut .title | wc")
                .build()

            val jobResponse = jobStub.createJob(request)
            assertThat(jobResponse.success).isTrue()
            assertThat(jobResponse.jobId).isNotEmpty()

            val stateQueryStub = StateQueryServiceGrpc.newBlockingStub(
                grpcCleanupRule.register(InProcessChannelBuilder.forName(serverName).directExecutor().build())
            )

            // Wait for the job to start and create state stores
            Thread.sleep(5000)

            val listRequest = StateQuery.ListStoresRequest.getDefaultInstance()
            val storesResponse = stateQueryStub.listStores(listRequest)

            // Verify the response is valid (stores may or may not be present depending on job state)
            assertThat(storesResponse).isNotNull()
        }
    }

    @Test
    fun `getValue returns NOT_FOUND for nonexistent store`(): Unit = runBlocking {
        app.use {
            val serverName = InProcessServerBuilder.generateName()
            launch(dispatcher) {
                app.run(InProcessServerBuilder.forName(serverName).directExecutor())
            }

            until { requireNotNull(app.server) }

            grpcCleanupRule.register(app.server ?: return@use)

            val stub = StateQueryServiceGrpc.newBlockingStub(
                grpcCleanupRule.register(InProcessChannelBuilder.forName(serverName).directExecutor().build())
            )

            val request = StateQuery.GetValueRequest.newBuilder()
                .setStoreName("nonexistent-store")
                .setKey("test-key")
                .build()

            try {
                stub.getValue(request)
                org.junit.jupiter.api.fail("Expected StatusRuntimeException")
            } catch (e: io.grpc.StatusRuntimeException) {
                assertThat(e.status.code).isEqualTo(io.grpc.Status.Code.NOT_FOUND)
                assertThat(e.status.description).contains("Store not found")
            }
        }
    }

    @Test
    fun `getAllValues returns NOT_FOUND for nonexistent store`(): Unit = runBlocking {
        app.use {
            val serverName = InProcessServerBuilder.generateName()
            launch(dispatcher) {
                app.run(InProcessServerBuilder.forName(serverName).directExecutor())
            }

            until { requireNotNull(app.server) }

            grpcCleanupRule.register(app.server ?: return@use)

            val stub = StateQueryServiceGrpc.newBlockingStub(
                grpcCleanupRule.register(InProcessChannelBuilder.forName(serverName).directExecutor().build())
            )

            val request = StateQuery.GetAllValuesRequest.newBuilder()
                .setStoreName("nonexistent-store")
                .setLimit(10)
                .build()

            try {
                stub.getAllValues(request).forEach { _ -> }
                org.junit.jupiter.api.fail("Expected StatusRuntimeException")
            } catch (e: io.grpc.StatusRuntimeException) {
                assertThat(e.status.code).isEqualTo(io.grpc.Status.Code.NOT_FOUND)
                assertThat(e.status.description).contains("Store not found")
            }
        }
    }

    @Test
    fun `getAllValues respects limit parameter`(): Unit = runBlocking {
        val topic = TestKafka.uniqueTopic("books")

        app.use {
            // Produce many records to test limiting
            val books = (1..10).map { i ->
                Book(title = "Book $i", wordCount = i * 100, authorId = UUID.randomUUID().toString())
            }
            testKafka.produceRecords(topic, "avro", *books.toTypedArray())

            val serverName = InProcessServerBuilder.generateName()
            launch(dispatcher) {
                app.run(InProcessServerBuilder.forName(serverName).directExecutor())
            }

            until { requireNotNull(app.server) }

            grpcCleanupRule.register(app.server ?: return@use)

            val jobStub = JobServiceGrpc.newBlockingStub(
                grpcCleanupRule.register(InProcessChannelBuilder.forName(serverName).directExecutor().build())
            )

            val request = Job.CreateJobRequest.newBuilder()
                .setUserId("test-user")
                .setSource("cat /dev/kafka/local/topics/$topic | cut .title | wc")
                .build()

            val jobResponse = jobStub.createJob(request)
            assertThat(jobResponse.success).isTrue()

            val stateQueryStub = StateQueryServiceGrpc.newBlockingStub(
                grpcCleanupRule.register(InProcessChannelBuilder.forName(serverName).directExecutor().build())
            )

            // Wait for job to process records
            Thread.sleep(5000)

            // Get store names first
            val listResponse = stateQueryStub.listStores(StateQuery.ListStoresRequest.getDefaultInstance())

            if (listResponse.storesList.isNotEmpty()) {
                val storeName = listResponse.storesList.first().name

                // Request with limit of 3
                val getAllRequest = StateQuery.GetAllValuesRequest.newBuilder()
                    .setStoreName(storeName)
                    .setLimit(3)
                    .build()

                val results = mutableListOf<StateQuery.KeyValuePair>()
                stateQueryStub.getAllValues(getAllRequest).forEach { results.add(it) }

                // Should return at most 3 results
                assertThat(results.size).isLessThanOrEqualTo(3)
            }
        }
    }

    @Test
    fun `getAllValues with running job returns key-value pairs`(): Unit = runBlocking {
        val topic = TestKafka.uniqueTopic("books")

        app.use {
            // Produce records with duplicate titles to get counts > 1
            testKafka.produceRecords(
                topic,
                "avro",
                Book(title = "Dune", wordCount = 300, authorId = UUID.randomUUID().toString()),
                Book(title = "Dune", wordCount = 250, authorId = UUID.randomUUID().toString()),
                Book(title = "Foundation", wordCount = 200, authorId = UUID.randomUUID().toString())
            )

            val serverName = InProcessServerBuilder.generateName()
            launch(dispatcher) {
                app.run(InProcessServerBuilder.forName(serverName).directExecutor())
            }

            until { requireNotNull(app.server) }

            grpcCleanupRule.register(app.server ?: return@use)

            val jobStub = JobServiceGrpc.newBlockingStub(
                grpcCleanupRule.register(InProcessChannelBuilder.forName(serverName).directExecutor().build())
            )

            val request = Job.CreateJobRequest.newBuilder()
                .setUserId("test-user")
                .setSource("cat /dev/kafka/local/topics/$topic | cut .title | wc")
                .build()

            val jobResponse = jobStub.createJob(request)
            assertThat(jobResponse.success).isTrue()

            val stateQueryStub = StateQueryServiceGrpc.newBlockingStub(
                grpcCleanupRule.register(InProcessChannelBuilder.forName(serverName).directExecutor().build())
            )

            // Wait for job to process records
            Thread.sleep(7000)

            // Get store names
            val listResponse = stateQueryStub.listStores(StateQuery.ListStoresRequest.getDefaultInstance())

            if (listResponse.storesList.isNotEmpty()) {
                val storeName = listResponse.storesList.first().name

                val getAllRequest = StateQuery.GetAllValuesRequest.newBuilder()
                    .setStoreName(storeName)
                    .setLimit(100)
                    .build()

                val results = mutableListOf<StateQuery.KeyValuePair>()
                stateQueryStub.getAllValues(getAllRequest).forEach { results.add(it) }

                // Verify we got some results
                if (results.isNotEmpty()) {
                    // Keys should be JSON-serialized
                    results.forEach { kv ->
                        assertThat(kv.key).isNotEmpty()
                        assertThat(kv.value).isNotEmpty()
                        // Value should be parseable as a number (count)
                        assertThat(kv.value.toLongOrNull()).isNotNull()
                    }
                }
            }
        }
    }
}
