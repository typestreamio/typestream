package io.typestream.compiler

import io.typestream.compiler.parser.Parser
import io.typestream.compiler.vm.Environment
import io.typestream.compiler.vm.Session
import io.typestream.config.SourcesConfig
import io.typestream.filesystem.FileSystem
import io.typestream.scheduler.Scheduler
import io.typestream.testing.RedpandaContainerWrapper
import io.typestream.testing.avro.buildBook
import io.typestream.testing.konfig.testKonfig
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.util.UUID


@OptIn(ExperimentalCoroutinesApi::class)
@Testcontainers
internal class InterpreterTest {

    @Container
    private val testKafka = RedpandaContainerWrapper()

    private lateinit var fileSystem: FileSystem

    private lateinit var environment: Environment

    private val testDispatcher = UnconfinedTestDispatcher()

    @BeforeEach
    fun beforeEach() {
        val sourcesConfig = SourcesConfig(testKonfig(testKafka))
        fileSystem = io.typestream.filesystem.FileSystem(sourcesConfig, testDispatcher)
        environment = Environment(fileSystem, Scheduler(sourcesConfig, dispatcher = testDispatcher), Session())
    }

    @Test
    fun `handles non-existing fields on conditions`() = runTest(testDispatcher) {
        fileSystem.use {
            testKafka.produceRecords("books", buildBook("Station Eleven", 300, UUID.randomUUID()))

            launch {
                fileSystem.watch()
            }

            val statements =
                Parser("cat /dev/kafka/local/topics/books | grep [ .notTheTitle == 'Station Eleven' ]").parse()

            val analyzer = Interpreter(environment)

            statements.forEach { it.accept(analyzer) }

            assertThat(analyzer.errors).hasSize(1)
                .containsExactly(
                    """
                    cannot find field 'notTheTitle' in /dev/kafka/local/topics/books.
                    You can use 'file /dev/kafka/local/topics/books' to check available fields
                """.trimIndent()
                )
        }
    }

    @Test
    fun `handles non-existing fields on projections`() = runTest(testDispatcher) {
        fileSystem.use {
            testKafka.produceRecords("books", buildBook("Station Eleven", 300, UUID.randomUUID()))

            launch {
                fileSystem.watch()
            }

            val statements = Parser("cat /dev/kafka/local/topics/books | cut .notTheTitle").parse()

            val analyzer = Interpreter(environment)

            statements.forEach { it.accept(analyzer) }

            assertThat(analyzer.errors).hasSize(1)
                .containsExactly(
                    """
                    cannot find field 'notTheTitle' in /dev/kafka/local/topics/books.
                    You can use 'file /dev/kafka/local/topics/books' to check available fields
                """.trimIndent()
                )
        }
    }
}
