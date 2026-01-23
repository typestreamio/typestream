package io.typestream.compiler.shellcommand

import io.typestream.compiler.shellcommand.ShellCommandOutput.Companion.withError
import io.typestream.compiler.shellcommand.ShellCommandOutput.Companion.withOutput
import io.typestream.compiler.types.DataStream
import io.typestream.compiler.types.schema.Schema
import io.typestream.compiler.types.schema.fromJSON
import io.typestream.compiler.vm.Env
import io.typestream.compiler.vm.Session
import io.typestream.config.testing.testConfig
import io.typestream.filesystem.FileSystem
import io.typestream.scheduler.Scheduler
import io.typestream.testing.TestKafka
import kotlinx.coroutines.Dispatchers
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers


@Testcontainers
internal class HttpKtTest {
    companion object {
        @Container
        @JvmStatic
        private val testKafka = TestKafka()
    }

    private lateinit var fileSystem: FileSystem
    private lateinit var session: Session

    private lateinit var mockWebServer: MockWebServer

    @BeforeEach
    fun beforeEach() {
        val testConfig = testConfig(testKafka)
        fileSystem = FileSystem(testConfig, Dispatchers.IO)
        session = Session(fileSystem, Scheduler(false, Dispatchers.IO), Env(testConfig))
        mockWebServer = MockWebServer()
        mockWebServer.start()
    }

    @Test
    fun `defaults to get`() {
        mockWebServer.enqueue(MockResponse().setBody("{}"))

        val baseUrl = mockWebServer.url("/").toString()
        val result = http(session, listOf(baseUrl))

        assertThat(result).isEqualTo(
            withOutput(listOf(DataStream("/bin/http", Schema.Struct.fromJSON("{}"))))
        )
    }

    @Nested
    inner class PostTest {
        @Test
        fun `requires body`() {
            val baseUrl = mockWebServer.url("/").toString()
            val result = http(session, listOf("post", baseUrl))

            assertThat(result).isEqualTo(withError("Usage: http post <url> <body>"))
        }

        @Test
        fun posts() {
            mockWebServer.enqueue(MockResponse().setBody("{}"))

            val baseUrl = mockWebServer.url("/").toString()
            val result = http(session, listOf("post", baseUrl, "{foo: 42}"))

            val request = mockWebServer.takeRequest()

            assertThat(request.headers["Content-Type"]).isEqualTo("application/json; charset=utf-8")
            assertThat(request.body.readUtf8()).isEqualTo("{foo: 42}")

            assertThat(request.method).isEqualTo("POST")

            assertThat(result).isEqualTo(
                withOutput(listOf(DataStream("/bin/http", Schema.Struct.fromJSON("{}"))))
            )
        }
    }
}
