package io.typestream.grpc

import io.grpc.inprocess.InProcessChannelBuilder
import io.grpc.inprocess.InProcessServerBuilder
import io.grpc.testing.GrpcCleanupRule
import io.typestream.App
import io.typestream.grpc.ProgramServiceGrpc.ProgramServiceBlockingStub
import io.typestream.testing.RedpandaContainerWrapper
import io.typestream.testing.avro.buildBook
import io.typestream.testing.avro.buildUser
import io.typestream.testing.konfig.testKonfig
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
import kotlin.test.assertTrue


@Testcontainers
internal class ProgramServiceTest {
    private val dispatcher = Dispatchers.IO

    private lateinit var app: App

    @get:Rule
    val grpcCleanupRule: GrpcCleanupRule = GrpcCleanupRule()

    @Container
    private val testKafka = RedpandaContainerWrapper()

    @BeforeEach
    fun beforeEach() {
        app = App(testKonfig(testKafka), dispatcher)
    }

    @Test
    fun `runs shell programs`(): Unit = runBlocking {
        app.use {
            val serverName = InProcessServerBuilder.generateName()
            launch(dispatcher) {
                app.run(InProcessServerBuilder.forName(serverName).directExecutor())
            }

            until { requireNotNull(app.server) }

            grpcCleanupRule.register(app.server!!)

            val stub = ProgramServiceGrpc.newBlockingStub(
                grpcCleanupRule.register(InProcessChannelBuilder.forName(serverName).directExecutor().build())
            )

            assertThat(stub.runProgram("ls")).extracting("stdOut", "stdErr", "hasMoreOutput")
                .containsExactly("dev", "", false)
        }
    }

    @Test
    fun `returns stdErr correctly`(): Unit = runBlocking {
        app.use {
            val serverName = InProcessServerBuilder.generateName()
            launch(dispatcher) {
                app.run(InProcessServerBuilder.forName(serverName).directExecutor())
            }

            until { requireNotNull(app.server) }

            grpcCleanupRule.register(app.server!!)

            val stub = ProgramServiceGrpc.newBlockingStub(
                grpcCleanupRule.register(InProcessChannelBuilder.forName(serverName).directExecutor().build())
            )

            assertThat(stub.runProgram("whatever")).extracting("stdOut", "stdErr", "hasMoreOutput")
                .containsExactly("", "franz: whatever not found", false)
        }
    }

    @Test
    fun `runs a one command pipeline`(): Unit = runBlocking {
        val users = testKafka.produceRecords("users", buildUser("Grace Hopper"))

        app.use {
            val serverName = InProcessServerBuilder.generateName()
            launch(dispatcher) {
                app.run(InProcessServerBuilder.forName(serverName).directExecutor())
            }

            until { requireNotNull(app.server) }

            grpcCleanupRule.register(app.server!!)

            val stub = ProgramServiceGrpc.newBlockingStub(
                grpcCleanupRule.register(InProcessChannelBuilder.forName(serverName).directExecutor().build())
            )

            val cat = stub.runProgram("cat /dev/kafka/local/topics/users")

            assertThat(cat).extracting("stdOut", "stdErr", "hasMoreOutput").containsExactly("", "", true)

            val responseStream =
                stub.getProgramOutput(
                    Program.GetProgramOutputRequest.newBuilder().setId(cat.id).build()
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
                app.run(InProcessServerBuilder.forName(serverName).directExecutor())
            }

            until { requireNotNull(app.server) }

            grpcCleanupRule.register(app.server!!)

            val stub = ProgramServiceGrpc.newBlockingStub(
                grpcCleanupRule.register(InProcessChannelBuilder.forName(serverName).directExecutor().build())
            )

            val cat = stub.runProgram("cat /dev/kafka/local/topics/users | grep \"Margaret\"")

            assertThat(cat).extracting("stdOut", "stdErr", "hasMoreOutput").containsExactly("", "", true)

            val responseStream =
                stub.getProgramOutput(
                    Program.GetProgramOutputRequest.newBuilder().setId(cat.id).build()
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
                app.run(InProcessServerBuilder.forName(serverName).directExecutor())
            }

            until { requireNotNull(app.server) }

            grpcCleanupRule.register(app.server!!)

            val stub = ProgramServiceGrpc.newBlockingStub(
                grpcCleanupRule.register(InProcessChannelBuilder.forName(serverName).directExecutor().build())
            )

            val cat = stub.runProgram("cat /dev/kafka/local/topics/books | cut title")

            assertThat(cat).extracting("stdOut", "stdErr", "hasMoreOutput").containsExactly("", "", true)

            val responseStream =
                stub.getProgramOutput(
                    Program.GetProgramOutputRequest.newBuilder().setId(cat.id).build()
                )
            val output = buildList {
                assertTrue(responseStream.hasNext())
                add(responseStream.next().stdOut)
                assertTrue(responseStream.hasNext())
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
                app.run(InProcessServerBuilder.forName(serverName).directExecutor())
            }

            until { requireNotNull(app.server) }

            grpcCleanupRule.register(app.server!!)

            val stub = ProgramServiceGrpc.newBlockingStub(
                grpcCleanupRule.register(InProcessChannelBuilder.forName(serverName).directExecutor().build())
            )

            val cat = stub.runProgram(
                "cat /dev/kafka/local/topics/users | grep 'Margaret' > /dev/kafka/local/topics/user_names"
            )

            assertThat(cat).extracting("stdOut", "stdErr", "hasMoreOutput").containsExactly("", "", false)

            until("file") { stub.runProgram("file /dev/kafka/local/topics/user_names") }

            val catNames = stub.runProgram("cat /dev/kafka/local/topics/user_names")

            until("ps") { assertThat(stub.runProgram("ps").stdOut).contains(catNames.id) }

            assertThat(catNames).extracting("stdOut", "stdErr", "hasMoreOutput").containsExactly("", "", true)

            val responseStream =
                stub.getProgramOutput(
                    Program.GetProgramOutputRequest.newBuilder().setId(catNames.id).build()
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

fun ProgramServiceBlockingStub.runProgram(source: String): Program.RunProgramResponse =
    runProgram(Program.RunProgramRequest.newBuilder().setSource(source).build())
