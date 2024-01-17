package io.typestream.compiler

import io.typestream.compiler.parser.Parser
import io.typestream.compiler.vm.Env
import io.typestream.compiler.vm.Session
import io.typestream.config.testing.testConfig
import io.typestream.filesystem.FileSystem
import io.typestream.scheduler.Scheduler
import io.typestream.testing.TestKafka
import io.typestream.testing.model.Book
import kotlinx.coroutines.Dispatchers
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.util.UUID


@Testcontainers
internal class InterpreterTest {

    @Container
    private val testKafka = TestKafka()

    private lateinit var fileSystem: FileSystem

    private lateinit var session: Session

    @BeforeEach
    fun beforeEach() {
        fileSystem = FileSystem(testConfig(testKafka).sources, Dispatchers.IO)
        session = Session(fileSystem, Scheduler(false, Dispatchers.IO), Env())
    }

    @ParameterizedTest
    @ValueSource(strings = ["avro", "proto"])
    fun `handles non-existing fields on conditions`(encoding: String) {
        fileSystem.use {
            testKafka.produceRecords(
                "books",
                encoding,
                Book(title = "Station Eleven", wordCount = 300, authorId = UUID.randomUUID().toString())
            )

            fileSystem.refresh()

            val statements =
                Parser("cat /dev/kafka/local/topics/books | grep [ .notTheTitle == 'Station Eleven' ]").parse()

            val analyzer = Interpreter(session)

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

    @ParameterizedTest
    @ValueSource(strings = ["avro", "proto"])
    fun `handles non-existing fields on projections`(encoding: String) {
        fileSystem.use {
            testKafka.produceRecords(
                "books",
                encoding,
                Book(title = "Station Eleven", wordCount = 300, authorId = UUID.randomUUID().toString())
            )

            fileSystem.refresh()

            val statements = Parser("cat /dev/kafka/local/topics/books | cut .notTheTitle").parse()

            val analyzer = Interpreter(session)

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
