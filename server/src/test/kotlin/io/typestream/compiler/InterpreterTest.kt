package io.typestream.compiler

import io.typestream.compiler.parser.Parser
import io.typestream.compiler.vm.Env
import io.typestream.compiler.vm.Session
import io.typestream.config.testing.testConfig
import io.typestream.filesystem.FileSystem
import io.typestream.scheduler.Scheduler
import io.typestream.testing.TestKafka
import io.typestream.testing.TestKafkaContainer
import io.typestream.testing.model.Book
import kotlinx.coroutines.Dispatchers
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.testcontainers.junit.jupiter.Testcontainers
import java.util.UUID


@Testcontainers
internal class InterpreterTest {

    companion object {
        private val testKafka = TestKafkaContainer.instance
    }

    private lateinit var fileSystem: FileSystem

    private lateinit var session: Session

    @BeforeEach
    fun beforeEach() {
        val testConfig = testConfig(testKafka)
        fileSystem = FileSystem(testConfig, Dispatchers.IO)
        session = Session(fileSystem, Scheduler(false, Dispatchers.IO), Env(testConfig))
    }

    @ParameterizedTest
    @ValueSource(strings = ["avro", "proto"])
    fun `handles non-existing fields on conditions`(encoding: String) {
        val topic = TestKafka.uniqueTopic("books")

        fileSystem.use {
            testKafka.produceRecords(
                topic,
                encoding,
                Book(title = "Station Eleven", wordCount = 300, authorId = UUID.randomUUID().toString())
            )

            fileSystem.refresh()

            val statements =
                Parser("cat /dev/kafka/local/topics/$topic | grep [ .notTheTitle == 'Station Eleven' ]").parse()

            val analyzer = Interpreter(session)

            statements.forEach { it.accept(analyzer) }

            assertThat(analyzer.errors).hasSize(1)
                .containsExactly(
                    """
                    cannot find field 'notTheTitle' in /dev/kafka/local/topics/$topic.
                    You can use 'file /dev/kafka/local/topics/$topic' to check available fields
                """.trimIndent()
                )
        }
    }

    @ParameterizedTest
    @ValueSource(strings = ["avro", "proto"])
    fun `handles non-existing fields on projections`(encoding: String) {
        val topic = TestKafka.uniqueTopic("books")

        fileSystem.use {
            testKafka.produceRecords(
                topic,
                encoding,
                Book(title = "Station Eleven", wordCount = 300, authorId = UUID.randomUUID().toString())
            )

            fileSystem.refresh()

            val statements = Parser("cat /dev/kafka/local/topics/$topic | cut .notTheTitle").parse()

            val analyzer = Interpreter(session)

            statements.forEach { it.accept(analyzer) }

            assertThat(analyzer.errors).hasSize(1)
                .containsExactly(
                    """
                    cannot find field 'notTheTitle' in /dev/kafka/local/topics/$topic.
                    You can use 'file /dev/kafka/local/topics/$topic' to check available fields
                """.trimIndent()
                )
        }
    }
}
