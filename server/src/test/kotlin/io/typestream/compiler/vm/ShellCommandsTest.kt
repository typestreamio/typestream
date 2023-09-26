package io.typestream.compiler.vm

import io.typestream.compiler.ast.ShellCommand
import io.typestream.compiler.shellcommand.ShellCommandOutput
import io.typestream.compiler.shellcommand.find
import io.typestream.compiler.types.DataStream
import io.typestream.compiler.types.schema.Schema
import io.typestream.config.SourcesConfig
import io.typestream.filesystem.FileSystem
import io.typestream.scheduler.Scheduler
import io.typestream.testing.RedpandaContainerWrapper
import io.typestream.testing.avro.buildAuthor
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


@OptIn(ExperimentalCoroutinesApi::class)
@Testcontainers
internal class ShellCommandsTest {
    @Container
    private val testKafka = RedpandaContainerWrapper()

    private lateinit var fileSystem: FileSystem
    private lateinit var session: Session

    private val testDispatcher = UnconfinedTestDispatcher()

    @BeforeEach
    fun beforeEach() {
        val sourcesConfig = SourcesConfig(testKonfig(testKafka))

        fileSystem = FileSystem(sourcesConfig, testDispatcher)
        session = Session(fileSystem, Scheduler(false, testDispatcher), Env())
    }


    @Test
    fun `changes directory correctly`() = runTest {
        fileSystem.use {
            testKafka.produceRecords("authors", buildAuthor("Emily St. John Mandel"))
            launch(testDispatcher) { fileSystem.watch() }

            val cd = ShellCommand.find("cd")
            requireNotNull(cd)

            val shellCommandOutput = cd(session, listOf("dev/kafka/local/topics"))

            assertThat(shellCommandOutput)
                .isEqualTo(
                    ShellCommandOutput.withOutput(
                        listOf(
                            DataStream(
                                "/bin/cd",
                                Schema.String("/dev/kafka/local/topics")
                            )
                        )
                    )
                )

            assertThat(session.env.pwd).isEqualTo("/dev/kafka/local/topics")
        }
    }

    @Test
    fun `cannot change directory to incorrect path`() = runTest {
        fileSystem.use {
            testKafka.produceRecords("authors", buildAuthor("Octavia E. Butler"))
            launch(testDispatcher) { fileSystem.watch() }

            val cd = ShellCommand.find("cd")
            requireNotNull(cd)

            val programResult = cd(session, listOf("dev/whatever"))

            assertThat(programResult)
                .isEqualTo(ShellCommandOutput.withError("cd: cannot cd into dev/whatever: no such file or directory"))

            assertThat(session.env.pwd).isEqualTo("/")
        }
    }


    @Test
    fun `changes directory only to dirs`() = runTest {
        fileSystem.use {
            testKafka.produceRecords("authors", buildAuthor("Ann Leckie"))
            launch(testDispatcher) { fileSystem.watch() }

            val cd = ShellCommand.find("cd")
            requireNotNull(cd)

            val programResult = cd(session, listOf("dev/kafka/local/topics/authors"))

            assertThat(programResult).isEqualTo(
                ShellCommandOutput.withError("cd: cannot cd into dev/kafka/local/topics/authors: not a directory")
            )

            assertThat(session.env.pwd).isEqualTo("/")
        }
    }
}
