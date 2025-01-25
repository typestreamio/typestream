package io.typestream.grpc

import io.grpc.inprocess.InProcessChannelBuilder
import io.grpc.inprocess.InProcessServerBuilder
import io.grpc.testing.GrpcCleanupRule
import io.typestream.Server
import io.typestream.config.testing.testConfig
import io.typestream.grpc.filesystem_service.FileSystemServiceGrpc
import io.typestream.grpc.filesystem_service.lsRequest
import io.typestream.testing.TestKafka
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

@Testcontainers
internal class FileSystemServiceTest {
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
    fun `returns filesystem paths`(): Unit = runBlocking {
        app.use {
            val serverName = InProcessServerBuilder.generateName()
            launch(dispatcher) {
                app.run(InProcessServerBuilder.forName(serverName).directExecutor())
            }

            until { requireNotNull(app.server) }

            grpcCleanupRule.register(app.server ?: return@use)

            val stub = FileSystemServiceGrpc.newBlockingStub(
                grpcCleanupRule.register(InProcessChannelBuilder.forName(serverName).directExecutor().build())
            )

            val path = lsRequest { path = "/" }

            assertThat(stub.ls(path))
                .extracting("filesList")
                .isEqualTo(listOf("dev", "mnt"))

        }
    }

}
