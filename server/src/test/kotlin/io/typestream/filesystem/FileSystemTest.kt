package io.typestream.filesystem

import io.typestream.compiler.ast.Cat
import io.typestream.compiler.ast.Expr
import io.typestream.compiler.ast.Grep
import io.typestream.compiler.ast.Pipeline
import io.typestream.compiler.types.Encoding
import io.typestream.config.SourcesConfig
import io.typestream.helpers.author
import io.typestream.testing.RedpandaContainerWrapper
import io.typestream.testing.avro.buildAuthor
import io.typestream.testing.konfig.testKonfig
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
        private val testKafka = RedpandaContainerWrapper()

        @JvmStatic
        @BeforeAll
        fun beforeAll() {
            fileSystem = FileSystem(SourcesConfig(testKonfig(testKafka)), Dispatchers.IO)

            testKafka.produceRecords("authors", buildAuthor("Octavia E. Butler"))

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
    fun `completes correctly`(incompletePath: String, pwd: String, suggestions: List<String>) {
        assertThat(fileSystem.completePath(incompletePath, pwd)).contains(*suggestions.toTypedArray())
    }

    @ParameterizedTest
    @MethodSource("expandPathCases")
    fun `expands paths correctly`(path: String, pwd: String, expected: String?) {
        assertThat(fileSystem.expandPath(path, pwd)).isEqualTo(expected)
    }

    @Nested
    inner class EncodingRules {
        @Test
        fun `infers simple encoding`() {
            val dataCommand = Cat(listOf(Expr.BareWord("/dev/kafka/local/topics/authors")))

            dataCommand.dataStreams.add(author())

            assertThat(fileSystem.inferEncoding(dataCommand)).isEqualTo(Encoding.AVRO)
        }

        @Test
        fun `infers pipeline encoding`() {
            val cat = Cat(listOf(Expr.BareWord("/dev/kafka/local/topics/authors")))

            cat.dataStreams.add(author())

            val grep = Grep(listOf(Expr.BareWord("Butler")))

            val pipeline = Pipeline(listOf(cat, grep))

            assertThat(fileSystem.inferEncoding(pipeline)).isEqualTo(Encoding.AVRO)
        }
    }
}
