package io.typestream.filesystem

import io.typestream.compiler.ast.Cat
import io.typestream.compiler.ast.Expr
import io.typestream.compiler.ast.Grep
import io.typestream.compiler.ast.Join
import io.typestream.compiler.ast.Pipeline
import io.typestream.compiler.types.Encoding
import io.typestream.config.testing.testConfig
import io.typestream.helpers.author
import io.typestream.helpers.book
import io.typestream.testing.TestKafka
import io.typestream.testing.model.Author
import io.typestream.testing.model.Book
import kotlinx.coroutines.Dispatchers
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.util.stream.Stream

@Testcontainers
internal class FileSystemTest {
    companion object {
        private lateinit var fileSystem: FileSystem

        @Container
        private val testKafka = TestKafka()

        @JvmStatic
        @BeforeAll
        fun beforeAll() {
            fileSystem = FileSystem(testConfig(testKafka), Dispatchers.IO)

            val author = Author(name = "Octavia E. Butler")
            testKafka.produceRecords("authors", "avro", author)
            testKafka.produceRecords(
                "books",
                "proto",
                Book(title = "Parable of the Sower", authorId = author.id, wordCount = 100)
            )

            fileSystem.refresh()
        }

        @JvmStatic
        fun completePathCases(): Stream<Arguments> = Stream.of(
            Arguments.of("d", "/", listOf("dev/")),
            Arguments.of("/d", "/", listOf("/dev/")),
            Arguments.of("ka", "/dev", listOf("kafka/")),
            Arguments.of("kafka/lo", "/dev", listOf("kafka/local/")),
            Arguments.of("dev/kafka/lo", "/", listOf("dev/kafka/local/")),
            Arguments.of(
                "/dev/kafka/local/", "/", listOf(
                    "/dev/kafka/local/brokers/",
                    "/dev/kafka/local/consumer-groups/",
                    "/dev/kafka/local/topics/",
                    "/dev/kafka/local/schemas/"
                )
            ),
        )

        @JvmStatic
        fun expandPathCases(): Stream<Arguments> = Stream.of(
            Arguments.of("dev", "/", "/dev"),
            Arguments.of("dev/", "/", "/dev"),
            Arguments.of("kafka", "/dev", "/dev/kafka"),
            Arguments.of("/dev/kafka", "/", "/dev/kafka"),
            Arguments.of("/dev/kafka", "/dev", "/dev/kafka"),
            Arguments.of("", "/", "/"),
            Arguments.of("..", "/dev", "/"),
            Arguments.of("..", "/dev/kafka", "/dev"),
            Arguments.of("dev/whatever", "/", null),
        )
    }

    @ParameterizedTest
    @MethodSource("completePathCases")
    fun completes(incompletePath: String, pwd: String, suggestions: List<String>) {
        assertThat(fileSystem.completePath(incompletePath, pwd)).contains(*suggestions.toTypedArray())
    }

    @ParameterizedTest
    @MethodSource("expandPathCases")
    fun `expands paths`(path: String, pwd: String, expected: String?) {
        assertThat(fileSystem.expandPath(path, pwd)).isEqualTo(expected)
    }

    @Nested
    inner class EncodingRules {
        @Test
        fun `infers avro encoding`() {
            val dataCommand = Cat(listOf(Expr.BareWord("/dev/kafka/local/topics/authors")))

            dataCommand.dataStreams.add(author())

            assertThat(fileSystem.inferEncoding(dataCommand)).isEqualTo(Encoding.AVRO)
        }

        @Test
        fun `infers proto encoding`() {
            val dataCommand = Cat(listOf(Expr.BareWord("/dev/kafka/local/topics/books")))

            dataCommand.dataStreams.add(book(title = "Parable of the Sower"))

            assertThat(fileSystem.inferEncoding(dataCommand)).isEqualTo(Encoding.PROTOBUF)
        }

        @Test
        fun `infers pipeline encoding`() {
            val cat = Cat(listOf(Expr.BareWord("/dev/kafka/local/topics/authors")))

            cat.dataStreams.add(author())

            val grep = Grep(listOf(Expr.BareWord("Butler")))

            val pipeline = Pipeline(listOf(cat, grep))

            assertThat(fileSystem.inferEncoding(pipeline)).isEqualTo(Encoding.AVRO)
        }


        @Test
        fun `infers mixed pipeline encoding`() {
            val cat = Cat(listOf(Expr.BareWord("/dev/kafka/local/topics/authors")))

            cat.dataStreams.add(author())

            val join = Join(listOf(Expr.BareWord("/dev/kafka/local/topics/books")))

            join.dataStreams.add(book(title = "Parable of the Sower"))

            val pipeline = Pipeline(listOf(cat, join))

            assertThat(fileSystem.inferEncoding(pipeline)).isEqualTo(Encoding.JSON)
        }
    }
}
