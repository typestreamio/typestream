package io.typestream.grpc

import io.github.oshai.kotlinlogging.KotlinLogging
import io.grpc.inprocess.InProcessChannelBuilder
import io.grpc.inprocess.InProcessServerBuilder
import io.grpc.testing.GrpcCleanupRule
import io.typestream.Server
import io.typestream.compiler.Program
import io.typestream.grpc.interactive_session_service.InteractiveSession.GetProgramOutputRequest
import io.typestream.grpc.interactive_session_service.InteractiveSession.RunProgramRequest
import io.typestream.grpc.interactive_session_service.InteractiveSession.RunProgramResponse
import io.typestream.grpc.interactive_session_service.InteractiveSession.StartSessionRequest
import io.typestream.grpc.interactive_session_service.InteractiveSessionServiceGrpc
import io.typestream.grpc.interactive_session_service.InteractiveSessionServiceGrpc.InteractiveSessionServiceBlockingStub
import io.typestream.testing.RedpandaContainerWrapper
import io.typestream.testing.avro.buildBook
import io.typestream.testing.avro.buildUser
import io.typestream.testing.konfig.testKonfig
import io.typestream.testing.until
import io.typestream.version_info.VersionInfo
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
import kotlin.test.assertTrue


@Testcontainers
internal class InteractiveSessionServiceTest {
    private val logger = KotlinLogging.logger {}
    private val dispatcher = Dispatchers.IO

    private lateinit var app: Server

    @get:Rule
    val grpcCleanupRule: GrpcCleanupRule = GrpcCleanupRule()

    @Container
    private val testKafka = RedpandaContainerWrapper()

    @BeforeEach
    fun beforeEach() {
        app = Server(testKonfig(testKafka), VersionInfo("beta", "n/a"), dispatcher)
    }

    @Test
    fun `runs shell programs`(): Unit = runBlocking {
        app.use {
            val serverName = InProcessServerBuilder.generateName()
            launch(dispatcher) {
                app.run(false, InProcessServerBuilder.forName(serverName).directExecutor())
            }

            until { requireNotNull(app.server) }

            grpcCleanupRule.register(app.server!!)

            val stub = InteractiveSessionServiceGrpc.newBlockingStub(
                grpcCleanupRule.register(InProcessChannelBuilder.forName(serverName).directExecutor().build())
            )

            val sessionId =
                stub.startSession(StartSessionRequest.newBuilder().setUserId("user_id").build()).sessionId

            assertThat(stub.runProgram(sessionId, "ls")).extracting("stdOut", "stdErr", "hasMoreOutput")
                .containsExactly("dev", "", false)
        }
    }

    @Test
    fun `runs sessions correctly`(): Unit = runBlocking {
        app.use {
            val serverName = InProcessServerBuilder.generateName()
            launch(dispatcher) {
                app.run(false, InProcessServerBuilder.forName(serverName).directExecutor())
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

            assertThat(stub.runProgram(sessionId,"ls \$localKafkaDir"))
                .extracting("stdOut", "stdErr", "hasMoreOutput")
                .containsExactly("brokers\nconsumer-groups\nschemas\ntopics", "", false)
        }
    }

    @Test
    fun `returns stdErr correctly`(): Unit = runBlocking {
        app.use {
            val serverName = InProcessServerBuilder.generateName()
            launch(dispatcher) {
                app.run(false, InProcessServerBuilder.forName(serverName).directExecutor())
            }

            until { requireNotNull(app.server) }

            grpcCleanupRule.register(app.server!!)

            val stub = InteractiveSessionServiceGrpc.newBlockingStub(
                grpcCleanupRule.register(InProcessChannelBuilder.forName(serverName).directExecutor().build())
            )

            val sessionId =
                stub.startSession(StartSessionRequest.newBuilder().setUserId("user_id").build()).sessionId

            assertThat(stub.runProgram(sessionId, "whatever")).extracting("stdOut", "stdErr", "hasMoreOutput")
                .containsExactly("", "typestream: whatever not found", false)
        }
    }

    @Test
    fun `runs a one command pipeline`(): Unit = runBlocking {
        val users = testKafka.produceRecords("users", buildUser("Grace Hopper"))

        app.use {
            val serverName = InProcessServerBuilder.generateName()
            launch(dispatcher) {
                app.run(false, InProcessServerBuilder.forName(serverName).directExecutor())
            }

            until { requireNotNull(app.server) }

            grpcCleanupRule.register(app.server!!)

            val stub = InteractiveSessionServiceGrpc.newBlockingStub(
                grpcCleanupRule.register(InProcessChannelBuilder.forName(serverName).directExecutor().build())
            )

            val sessionId =
                stub.startSession(StartSessionRequest.newBuilder().setUserId("user_id").build()).sessionId

            val cat = stub.runProgram(sessionId, "cat /dev/kafka/local/topics/users")

            assertThat(cat).extracting("stdOut", "stdErr", "hasMoreOutput").containsExactly("", "", true)

            val responseStream =
                stub.getProgramOutput(
                    GetProgramOutputRequest.newBuilder().setSessionId(sessionId).setId(cat.id).build()
                )

            assertTrue(responseStream.hasNext())
            val line = responseStream.next()

            val user = users.first()

            assertThat(line).extracting("stdOut").isEqualTo("{\"id\":\"${user.value().id}\",\"name\":\"Grace Hopper\"}")
        }
    }

    @Test
    fun `runs a filter pipeline`(): Unit = runBlocking {
        val users = testKafka.produceRecords(
            "users", buildUser("Grace Hopper"), buildUser("Margaret Hamilton")
        )

        app.use {
            val serverName = InProcessServerBuilder.generateName()
            launch(dispatcher) {
                app.run(false, InProcessServerBuilder.forName(serverName).directExecutor())
            }

            until { requireNotNull(app.server) }

            grpcCleanupRule.register(app.server!!)

            val stub = InteractiveSessionServiceGrpc.newBlockingStub(
                grpcCleanupRule.register(InProcessChannelBuilder.forName(serverName).directExecutor().build())
            )

            val sessionId = stub.startSession(StartSessionRequest.newBuilder().setUserId("user_id").build()).sessionId

            val cat = stub.runProgram(sessionId, "cat /dev/kafka/local/topics/users | grep \"Margaret\"")

            assertThat(cat).extracting("stdOut", "stdErr", "hasMoreOutput").containsExactly("", "", true)

            val responseStream =
                stub.getProgramOutput(
                    GetProgramOutputRequest.newBuilder().setSessionId(sessionId).setId(cat.id).build()
                )

            assertTrue(responseStream.hasNext())
            val line = responseStream.next()

            val user = users.find { it.value().name == "Margaret Hamilton" }
            requireNotNull(user)

            assertThat(line).extracting("stdOut")
                .isEqualTo("{\"id\":\"${user.value().id}\",\"name\":\"Margaret Hamilton\"}")
        }
    }

    @Test
    fun `runs a projection`(): Unit = runBlocking {
        val stationEleven = buildBook("Station Eleven", 42, UUID.randomUUID())
        val kindred = buildBook("Kindred", 42, UUID.randomUUID())
        testKafka.produceRecords("books", stationEleven, kindred)

        app.use {
            val serverName = InProcessServerBuilder.generateName()
            launch(dispatcher) {
                app.run(false, InProcessServerBuilder.forName(serverName).directExecutor())
            }

            until { requireNotNull(app.server) }

            grpcCleanupRule.register(app.server!!)

            val stub = InteractiveSessionServiceGrpc.newBlockingStub(
                grpcCleanupRule.register(InProcessChannelBuilder.forName(serverName).directExecutor().build())
            )

            val sessionId = stub.startSession(StartSessionRequest.newBuilder().setUserId("user_id").build()).sessionId

            val cat = stub.runProgram(sessionId, "cat /dev/kafka/local/topics/books | cut title")

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

    @Test
    fun `runs a redirection`(): Unit = runBlocking {
        val users = testKafka.produceRecords(
            "users", buildUser("Grace Hopper"), buildUser("Margaret Hamilton")
        )

        app.use {
            val serverName = InProcessServerBuilder.generateName()
            launch(dispatcher) {
                app.run(false, InProcessServerBuilder.forName(serverName).directExecutor())
            }

            until { requireNotNull(app.server) }

            grpcCleanupRule.register(app.server!!)

            val stub = InteractiveSessionServiceGrpc.newBlockingStub(
                grpcCleanupRule.register(InProcessChannelBuilder.forName(serverName).directExecutor().build())
            )

            val sessionId = stub.startSession(StartSessionRequest.newBuilder().setUserId("user_id").build()).sessionId

            val cat = stub.runProgram(
                sessionId,
                "cat /dev/kafka/local/topics/users | grep 'Margaret' > /dev/kafka/local/topics/user_names"
            )

            assertThat(cat).extracting("stdOut", "stdErr", "hasMoreOutput").containsExactly("", "", false)

            until("file") { stub.runProgram(sessionId, "file /dev/kafka/local/topics/user_names") }

            val catNames = stub.runProgram(sessionId, "cat /dev/kafka/local/topics/user_names")

            until("ps") { assertThat(stub.runProgram(sessionId, "ps").stdOut).contains(catNames.id) }

            assertThat(catNames).extracting("stdOut", "stdErr", "hasMoreOutput").containsExactly("", "", true)

            val responseStream = stub.getProgramOutput(
                GetProgramOutputRequest.newBuilder().setSessionId(sessionId).setId(catNames.id).build()
            )

            assertTrue(responseStream.hasNext())
            val line = responseStream.next()

            val user = users.find { it.value().name == "Margaret Hamilton" }
            requireNotNull(user)

            assertThat(line).extracting("stdOut")
                .isEqualTo("{\"id\":\"${user.value().id}\",\"name\":\"${user.value().name}\"}")
        }
    }

}

fun InteractiveSessionServiceBlockingStub.runProgram(sessionId: String, source: String): RunProgramResponse =
    runProgram(RunProgramRequest.newBuilder().setSessionId(sessionId).setSource(source).build())
