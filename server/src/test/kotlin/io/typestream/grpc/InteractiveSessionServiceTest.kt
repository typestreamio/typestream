package io.typestream.grpc

import io.grpc.inprocess.InProcessChannelBuilder
import io.grpc.inprocess.InProcessServerBuilder
import io.grpc.testing.GrpcCleanupRule
import io.typestream.Server
import io.typestream.config.testing.testConfig
import io.typestream.grpc.interactive_session_service.InteractiveSession.GetProgramOutputRequest
import io.typestream.grpc.interactive_session_service.InteractiveSession.RunProgramRequest
import io.typestream.grpc.interactive_session_service.InteractiveSession.RunProgramResponse
import io.typestream.grpc.interactive_session_service.InteractiveSession.StartSessionRequest
import io.typestream.grpc.interactive_session_service.InteractiveSessionServiceGrpc
import io.typestream.grpc.interactive_session_service.InteractiveSessionServiceGrpc.InteractiveSessionServiceBlockingStub
import io.typestream.testing.TestKafka
import io.typestream.testing.TestKafkaContainer
import io.typestream.testing.model.Book
import io.typestream.testing.model.User
import io.typestream.testing.until
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.Rule
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.testcontainers.junit.jupiter.Testcontainers
import java.util.UUID
import kotlin.test.assertTrue

@Testcontainers
internal class InteractiveSessionServiceTest {
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
    fun `runs shell programs`(): Unit = runBlocking {
        app.use {
            val serverName = InProcessServerBuilder.generateName()
            launch(dispatcher) {
                app.run(InProcessServerBuilder.forName(serverName).directExecutor())
            }

            until { requireNotNull(app.server) }

            grpcCleanupRule.register(app.server ?: return@use)

            val stub = InteractiveSessionServiceGrpc.newBlockingStub(
                grpcCleanupRule.register(InProcessChannelBuilder.forName(serverName).directExecutor().build())
            )

            val sessionId =
                stub.startSession(StartSessionRequest.newBuilder().setUserId("user_id").build()).sessionId

            assertThat(stub.runProgram(sessionId, "ls"))
                .extracting("stdOut", "stdErr", "hasMoreOutput")
                .containsExactly("dev\nmnt", "", false)

            assertThat(stub.runProgram(sessionId, "stat dev"))
                .extracting("stdOut", "stdErr", "hasMoreOutput")
                .containsExactly("File: dev\nchildren: 1\n", "", false)

            assertThat(stub.runProgram(sessionId, "file dev"))
                .extracting("stdOut", "stdErr", "hasMoreOutput")
                .containsExactly("directory", "", false)
        }
    }

    @Test
    fun `runs sessions`(): Unit = runBlocking {
        app.use {
            val serverName = InProcessServerBuilder.generateName()
            launch(dispatcher) {
                app.run(InProcessServerBuilder.forName(serverName).directExecutor())
            }

            until { requireNotNull(app.server) }

            grpcCleanupRule.register(app.server!!)

            val stub = InteractiveSessionServiceGrpc.newBlockingStub(
                grpcCleanupRule.register(InProcessChannelBuilder.forName(serverName).directExecutor().build())
            )
            val sessionId =
                stub.startSession(StartSessionRequest.newBuilder().setUserId("user_id").build()).sessionId

            assertThat(stub.runProgram(sessionId, "let localKafkaDir = \'/dev/kafka/local\'"))
                .extracting("stdOut", "stdErr", "hasMoreOutput")
                .containsExactly("", "", false)

            assertThat(stub.runProgram(sessionId, "ls \$localKafkaDir"))
                .extracting("stdOut", "stdErr", "hasMoreOutput")
                .containsExactly("brokers\nconsumer-groups\nschemas\ntopics", "", false)
        }
    }

    @Test
    fun `returns stdErr`(): Unit = runBlocking {
        app.use {
            val serverName = InProcessServerBuilder.generateName()
            launch(dispatcher) {
                app.run(InProcessServerBuilder.forName(serverName).directExecutor())
            }

            until { requireNotNull(app.server) }

            grpcCleanupRule.register(app.server ?: return@use)

            val stub = InteractiveSessionServiceGrpc.newBlockingStub(
                grpcCleanupRule.register(InProcessChannelBuilder.forName(serverName).directExecutor().build())
            )

            val sessionId =
                stub.startSession(StartSessionRequest.newBuilder().setUserId("user_id").build()).sessionId

            assertThat(stub.runProgram(sessionId, "whatever")).extracting("stdOut", "stdErr", "hasMoreOutput")
                .containsExactly("", "typestream: whatever not found", false)
        }
    }

    @ParameterizedTest
    @ValueSource(strings = ["avro", "proto"])
    fun `runs a one command pipeline`(encoding: String): Unit = runBlocking {
        val topic = TestKafka.uniqueTopic("users")
        val users = testKafka.produceRecords(topic, encoding, User(name = "Grace Hopper"))

        app.use {
            val serverName = InProcessServerBuilder.generateName()
            launch(dispatcher) {
                app.run(InProcessServerBuilder.forName(serverName).directExecutor())
            }

            until { requireNotNull(app.server) }

            grpcCleanupRule.register(app.server ?: return@use)

            val stub = InteractiveSessionServiceGrpc.newBlockingStub(
                grpcCleanupRule.register(InProcessChannelBuilder.forName(serverName).directExecutor().build())
            )

            val sessionId =
                stub.startSession(StartSessionRequest.newBuilder().setUserId("user_id").build()).sessionId

            val cat = stub.runProgram(sessionId, "cat /dev/kafka/local/topics/$topic")

            assertThat(cat).extracting("stdOut", "stdErr", "hasMoreOutput").containsExactly("", "", true)

            val responseStream =
                stub.getProgramOutput(
                    GetProgramOutputRequest.newBuilder().setSessionId(sessionId).setId(cat.id).build()
                )

            assertTrue(responseStream.hasNext())
            val line = responseStream.next()

            val user = users.first()

            assertThat(line).extracting("stdOut").isEqualTo("{\"id\":\"${user.id}\",\"name\":\"Grace Hopper\"}")
        }
    }

    @ParameterizedTest
    @ValueSource(strings = ["avro", "proto"])
    fun `runs a filter pipeline`(encoding: String): Unit = runBlocking {
        val topic = TestKafka.uniqueTopic("users")
        val users = testKafka.produceRecords(
            topic, encoding, User(name = "Grace Hopper"), User(name = "Margaret Hamilton")
        )

        app.use {
            val serverName = InProcessServerBuilder.generateName()
            launch(dispatcher) {
                app.run(InProcessServerBuilder.forName(serverName).directExecutor())
            }

            until { requireNotNull(app.server) }

            grpcCleanupRule.register(app.server ?: return@use)

            val stub = InteractiveSessionServiceGrpc.newBlockingStub(
                grpcCleanupRule.register(InProcessChannelBuilder.forName(serverName).directExecutor().build())
            )

            val sessionId = stub.startSession(StartSessionRequest.newBuilder().setUserId("user_id").build()).sessionId

            val cat = stub.runProgram(sessionId, "cat /dev/kafka/local/topics/$topic | grep \"Margaret\"")

            assertThat(cat).extracting("stdOut", "stdErr", "hasMoreOutput").containsExactly("", "", true)

            val responseStream =
                stub.getProgramOutput(
                    GetProgramOutputRequest.newBuilder().setSessionId(sessionId).setId(cat.id).build()
                )

            assertThat(responseStream.hasNext()).isTrue
            val line = responseStream.next()

            val user = users.find { require(it is User); it.name == "Margaret Hamilton" }
            requireNotNull(user)

            assertThat(line).extracting("stdOut").isEqualTo("{\"id\":\"${user.id}\",\"name\":\"Margaret Hamilton\"}")
        }
    }

    @ParameterizedTest
    @ValueSource(strings = ["avro", "proto"])
    fun `runs a projection`(encoding: String): Unit = runBlocking {
        val topic = TestKafka.uniqueTopic("books")
        val stationEleven = Book(title = "Station Eleven", wordCount = 42, authorId = UUID.randomUUID().toString())
        val kindred = Book(title = "Kindred", wordCount = 42, authorId = UUID.randomUUID().toString())
        testKafka.produceRecords(topic, encoding, stationEleven, kindred)

        app.use {
            val serverName = InProcessServerBuilder.generateName()
            launch(dispatcher) {
                app.run(InProcessServerBuilder.forName(serverName).directExecutor())
            }

            until { requireNotNull(app.server) }

            grpcCleanupRule.register(app.server ?: return@use)

            val stub = InteractiveSessionServiceGrpc.newBlockingStub(
                grpcCleanupRule.register(InProcessChannelBuilder.forName(serverName).directExecutor().build())
            )

            val sessionId = stub.startSession(StartSessionRequest.newBuilder().setUserId("user_id").build()).sessionId

            val cat = stub.runProgram(sessionId, "cat /dev/kafka/local/topics/$topic | cut title")

            assertThat(cat).extracting("stdOut", "stdErr", "hasMoreOutput").containsExactly("", "", true)

            val responseStream = stub.getProgramOutput(
                GetProgramOutputRequest.newBuilder().setSessionId(sessionId).setId(cat.id).build()
            )
            val output = buildList {
                assertTrue(responseStream.hasNext(), "expected more output")
                add(responseStream.next().stdOut)
                assertTrue(responseStream.hasNext(), "expected more output")
                add(responseStream.next().stdOut)
            }

            assertThat(output).containsExactlyInAnyOrder(
                "{\"title\":\"${stationEleven.title}\"}", "{\"title\":\"${kindred.title}\"}"
            )
        }
    }

    @ParameterizedTest
    @ValueSource(strings = ["avro", "proto"])
    fun `runs a redirection`(encoding: String): Unit = runBlocking {
        // Note: Using encoding-specific topic names to avoid conflicts when running both avro and proto tests
        // Cannot use fully unique topics because the redirection creates an output topic dynamically
        // and proto schema registration with unique output topic names has timing issues
        val usersTopic = "users_redir_$encoding"
        val userNamesTopic = "user_names_redir_$encoding"
        val users = testKafka.produceRecords(
            usersTopic, encoding, User(name = "Grace Hopper"), User(name = "Margaret Hamilton")
        )

        app.use {
            val serverName = InProcessServerBuilder.generateName()
            launch(dispatcher) {
                app.run(InProcessServerBuilder.forName(serverName).directExecutor())
            }

            until { requireNotNull(app.server) }

            grpcCleanupRule.register(app.server ?: return@use)

            val stub = InteractiveSessionServiceGrpc.newBlockingStub(
                grpcCleanupRule.register(InProcessChannelBuilder.forName(serverName).directExecutor().build())
            )

            val sessionId = stub.startSession(StartSessionRequest.newBuilder().setUserId("user_id").build()).sessionId

            val cat = stub.runProgram(
                sessionId,
                "cat /dev/kafka/local/topics/$usersTopic | grep 'Margaret' > /dev/kafka/local/topics/$userNamesTopic"
            )

            assertThat(cat).extracting("stdOut", "stdErr", "hasMoreOutput")
                .containsExactly("running ${cat.id} in the background", "", false)

            until("file") { stub.runProgram(sessionId, "file /dev/kafka/local/topics/$userNamesTopic") }

            val catNames = stub.runProgram(sessionId, "cat /dev/kafka/local/topics/$userNamesTopic")

            until("ps") { assertThat(stub.runProgram(sessionId, "ps").stdOut).contains(catNames.id) }

            assertThat(catNames).extracting("stdOut", "stdErr", "hasMoreOutput").containsExactly("", "", true)

            val responseStream = stub.getProgramOutput(
                GetProgramOutputRequest.newBuilder().setSessionId(sessionId).setId(catNames.id).build()
            )

            assertTrue(responseStream.hasNext())
            val line = responseStream.next()

            val user = users.find { require(it is User); it.name == "Margaret Hamilton" }
            requireNotNull(user)
            require(user is User)

            assertThat(line).extracting("stdOut").isEqualTo("{\"id\":\"${user.id}\",\"name\":\"${user.name}\"}")
        }
    }

}

fun InteractiveSessionServiceBlockingStub.runProgram(sessionId: String, source: String): RunProgramResponse =
    runProgram(RunProgramRequest.newBuilder().setSessionId(sessionId).setSource(source).build())
