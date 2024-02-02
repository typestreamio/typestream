package io.typestream.compiler.shellcommand

import io.typestream.compiler.ast.ShellCommand
import io.typestream.compiler.types.DataStream
import io.typestream.compiler.types.schema.Schema
import io.typestream.compiler.vm.Env
import io.typestream.compiler.vm.Session
import io.typestream.config.testing.testConfig
import io.typestream.filesystem.FileSystem
import io.typestream.scheduler.Scheduler
import io.typestream.testing.TestKafka
import io.typestream.testing.model.Author
import kotlinx.coroutines.Dispatchers
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers


@Testcontainers
internal class CdKtTest {
    @Container
    private val testKafka = TestKafka()

    private lateinit var fileSystem: FileSystem
    private lateinit var session: Session

    @BeforeEach
    fun beforeEach() {
        val testConfig = testConfig(testKafka)
        fileSystem = FileSystem(testConfig, Dispatchers.IO)
        session = Session(fileSystem, Scheduler(false, Dispatchers.IO), Env(testConfig))
    }

    @Test
    fun `changes directory correctly`() {
        fileSystem.use {
            val cd = ShellCommand.mustFind("cd")

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
    fun `cannot change directory to incorrect path`() {
        fileSystem.use {
            val cd = ShellCommand.mustFind("cd")

            val programResult = cd(session, listOf("dev/whatever"))

            assertThat(programResult).isEqualTo(
                ShellCommandOutput.withError("cd: cannot cd into dev/whatever: no such file or directory")
            )

            assertThat(session.env.pwd).isEqualTo("/")
        }
    }


    @ParameterizedTest
    @ValueSource(strings = ["avro", "proto"])
    fun `changes directory only to dirs`(encoding: String) {
        fileSystem.use {
            testKafka.produceRecords("authors", encoding, Author(name = "Ann Leckie"))
            fileSystem.refresh()

            val cd = ShellCommand.mustFind("cd")

            val programResult = cd(session, listOf("dev/kafka/local/topics/authors"))

            assertThat(programResult).isEqualTo(
                ShellCommandOutput.withError("cd: cannot cd into dev/kafka/local/topics/authors: not a directory")
            )

            assertThat(session.env.pwd).isEqualTo("/")
        }
    }
}
