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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
@Testcontainers
internal class FileSystemTest {

    @Container
    private val testKafka = RedpandaContainerWrapper()

    private lateinit var fileSystem: FileSystem

    private val testDispatcher = UnconfinedTestDispatcher()

    @BeforeEach
    fun beforeEach() {
        fileSystem = FileSystem(SourcesConfig(testKonfig(testKafka)), testDispatcher)
    }

    @Test
    fun `expands paths correctly`() {
        fileSystem.use {
            assertThat(fileSystem.expandPath("dev", "/")).isEqualTo("/dev");
            assertThat(fileSystem.expandPath("dev/", "/")).isEqualTo("/dev");
            assertThat(fileSystem.expandPath("kafka", "/dev")).isEqualTo("/dev/kafka");
            assertThat(fileSystem.expandPath("", "/")).isEqualTo("/");
            assertThat(fileSystem.expandPath("..", "/dev")).isEqualTo("/");
            assertNull(fileSystem.expandPath("dev/whatever", "/"));
        }
    }

    @Nested
    inner class EncodingRules {
        @Test
        fun `infers simple encoding`() = runTest(testDispatcher) {
            fileSystem.use {
                testKafka.produceRecords("authors", buildAuthor("Octavia E. Butler"))

                launch {
                    fileSystem.watch()
                }

                val dataCommand = Cat(listOf(Expr.BareWord("/dev/kafka/local/topics/authors")))

                dataCommand.dataStreams.add(author())

                assertThat(fileSystem.inferEncoding(dataCommand)).isEqualTo(Encoding.AVRO)
            }
        }

        @Test
        fun `infers pipeline encoding`() = runTest(testDispatcher) {
            fileSystem.use {
                testKafka.produceRecords("authors", buildAuthor("Emily St. John Mandel"))

                launch {
                    fileSystem.watch()
                }

                val cat = Cat(listOf(Expr.BareWord("/dev/kafka/local/topics/authors")))

                cat.dataStreams.add(author())

                val grep = Grep(listOf(Expr.BareWord("Mandel")))

                val pipeline = Pipeline(listOf(cat, grep))

                assertThat(fileSystem.inferEncoding(pipeline)).isEqualTo(Encoding.AVRO)
            }
        }
    }

    @Nested
    inner class CompletePath {
        @Test
        fun `completes correctly`() = runTest(testDispatcher) {
            fileSystem.use {
                assertThat(fileSystem.completePath("d", "/")).containsExactly("/dev")
                assertThat(fileSystem.completePath("/d", "/")).containsExactly("/dev")
                assertThat(fileSystem.completePath("ka", "/dev")).containsExactly("/dev/kafka")
                assertThat(fileSystem.completePath("/dev/kafka/local/", "/")).containsExactly(
                    "/dev/kafka/local/brokers",
                    "/dev/kafka/local/consumer-groups",
                    "/dev/kafka/local/topics",
                    "/dev/kafka/local/schemas"
                )
            }
        }

    }
}
