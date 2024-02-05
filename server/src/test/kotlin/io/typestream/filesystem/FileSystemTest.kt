package io.typestream.filesystem

import io.typestream.config.testing.testConfig
import io.typestream.testing.TestKafka
import kotlinx.coroutines.Dispatchers
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.util.stream.Stream
import kotlin.io.path.createTempDirectory

@Testcontainers
internal class FileSystemTest {
    private lateinit var fileSystem: FileSystem

    @Container
    private val testKafka = TestKafka()

    @BeforeEach
    fun beforeEach() {
        fileSystem = FileSystem(testConfig(testKafka, createTempDirectory().toString()), Dispatchers.IO)
    }

    companion object {
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
    inner class Mounting {
        @Test
        fun `mounts a directory`() {
            fileSystem.mount(
                """
                [mounts.random.test]
                endpoint = "/mount/random/test"
                valueType = "int"
            """.trimIndent()
            )

            assertThat(fileSystem.ls("/mnt/random")).contains("test")
        }

        @Test
        fun `unmounts a directory`() {
            fileSystem.mount(
                """
                [mounts.random.test]
                endpoint = "/mount/random/test"
                valueType = "int"
            """.trimIndent()
            )

            assertThat(fileSystem.ls("/mnt/random")).contains("test")

            fileSystem.unmount("/mnt/random/test")

            assertThat(fileSystem.ls("/mnt/random")).doesNotContain("test")
        }
    }
}
