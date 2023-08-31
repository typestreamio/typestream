package io.typestream.compiler

import io.typestream.compiler.lexer.CursorPosition
import io.typestream.compiler.vm.Environment
import io.typestream.compiler.vm.Session
import io.typestream.config.SourcesConfig
import io.typestream.filesystem.FileSystem
import io.typestream.scheduler.Scheduler
import io.typestream.testing.RedpandaContainerWrapper
import io.typestream.testing.konfig.testKonfig
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers

@Testcontainers
@OptIn(ExperimentalCoroutinesApi::class)
internal class CompilerTest {

    @Container
    private val testKafka = RedpandaContainerWrapper()

    private lateinit var fileSystem: FileSystem

    private lateinit var environment: Environment

    private val testDispatcher = UnconfinedTestDispatcher()

    @BeforeEach
    fun beforeEach() {
        val sourcesConfig = SourcesConfig(testKonfig(testKafka))
        fileSystem = FileSystem(sourcesConfig, testDispatcher)
        environment = Environment(fileSystem, Scheduler(sourcesConfig, dispatcher = testDispatcher), Session())
    }

    @Nested
    inner class Completion {
        @Test
        fun `completes simple program`() {
            val source = "c"

            val compiler = Compiler(environment)
            val suggestions = compiler.complete(source, CursorPosition(0, 1))

            assertThat(suggestions).containsExactly("cat", "cd", "cut")
        }

        @Test
        fun `completes simple data program with path`() {
            val source = "cat /de"

            val compiler = Compiler(environment)
            val suggestions = compiler.complete(source, CursorPosition(0, 7))

            assertThat(suggestions).containsExactly("/dev")
        }

        @Disabled("TODO need to rethink how to handle this")
        fun `completes empty expressions with paths`() {
            val source = "cd "

            val compiler = Compiler(environment)
            val suggestions = compiler.complete(source, CursorPosition(0, 3))

            assertThat(suggestions).containsExactly("/dev")
        }

        @Test
        fun `completes simple shell program with path`() {
            val source = "cd /de"

            val compiler = Compiler(environment)
            val suggestions = compiler.complete(source, CursorPosition(0, 6))

            assertThat(suggestions).containsExactly("/dev")
        }

        @Test
        fun `completes simple program with pipe`() {
            val source = "cat /dev/kafka/local/topics/books | "

            val compiler = Compiler(environment)
            val suggestions = compiler.complete(source, CursorPosition(0, 31))

            assertThat(suggestions).containsExactly("cat", "cut", "echo", "enrich", "grep", "join", "let", "wc")
        }
    }
}
