package io.typestream.compiler

import io.typestream.compiler.ast.Predicate
import io.typestream.compiler.lexer.CursorPosition
import io.typestream.compiler.node.Node
import io.typestream.compiler.types.DataStream
import io.typestream.compiler.types.datastream.fromAvroSchema
import io.typestream.compiler.types.schema.Schema
import io.typestream.compiler.vm.Env
import io.typestream.compiler.vm.Session
import io.typestream.config.SourcesConfig
import io.typestream.filesystem.FileSystem
import io.typestream.scheduler.Scheduler
import io.typestream.testing.TestKafka
import io.typestream.testing.konfig.testKonfig
import io.typestream.testing.model.Book
import kotlinx.coroutines.Dispatchers
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.util.UUID
import io.typestream.testing.avro.Book as AvroBook

@Testcontainers
internal class CompilerTest {
    @Container
    private val testKafka = TestKafka()

    private lateinit var fileSystem: FileSystem

    private lateinit var session: Session

    private val testDispatcher = Dispatchers.IO

    @BeforeEach
    fun beforeEach() {
        val sourcesConfig = SourcesConfig(testKonfig(testKafka))
        fileSystem = FileSystem(sourcesConfig, testDispatcher)
        session = Session(fileSystem, Scheduler(false, testDispatcher), Env())
    }

    @Nested
    inner class Completion {
        @Test
        fun `completes simple program`() {
            val source = "c"

            val compiler = Compiler(session)
            val suggestions = compiler.complete(source, CursorPosition(0, 1))

            assertThat(suggestions).containsExactly("cat", "cd", "cut")
        }

        @Test
        fun `completes simple data program with path`() {
            val source = "cat /de"

            val compiler = Compiler(session)
            val suggestions = compiler.complete(source, CursorPosition(0, 7))

            assertThat(suggestions).containsExactly("/dev/")
        }

        @Disabled("TODO need to rethink how to handle this")
        fun `completes empty expressions with paths`() {
            val source = "cd "

            val compiler = Compiler(session)
            val suggestions = compiler.complete(source, CursorPosition(0, 3))

            assertThat(suggestions).containsExactly("/dev")
        }

        @Test
        fun `completes simple shell program with path`() {
            val source = "cd /de"

            val compiler = Compiler(session)
            val suggestions = compiler.complete(source, CursorPosition(0, 6))

            assertThat(suggestions).containsExactly("/dev/")
        }

        @Test
        fun `completes simple program with pipe`() {
            val source = "cat /dev/kafka/local/topics/books | "

            val compiler = Compiler(session)
            val suggestions = compiler.complete(source, CursorPosition(0, 31))

            assertThat(suggestions).containsExactly("cat", "cut",  "each", "echo", "enrich", "grep", "join", "let", "wc")
        }
    }

    @Nested
    inner class Grep {
        @BeforeEach
        fun beforeEach() {
            testKafka.produceRecords(
                "books",
                "avro",
                Book(title = "Station Eleven", wordCount = 300, authorId = UUID.randomUUID().toString())
            )

            fileSystem.refresh()
        }

        @Test
        fun `compiles simple grep correctly`() {
            val source = "grep 'Station Eleven' /dev/kafka/local/topics/books"

            val compiler = Compiler(session)
            val compilerResult = compiler.compile(source)

            assertThat(compilerResult.errors).isEmpty()

            val program = compilerResult.program

            assertThat(program.graph.children).hasSize(1)
            val streamSourceNode = program.graph.children.first().ref
            require(streamSourceNode is Node.StreamSource)

            assertThat(streamSourceNode.dataStream).isEqualTo(
                DataStream.fromAvroSchema("/dev/kafka/local/topics/books", AvroBook.`SCHEMA$`)
            )

            assertThat(program.graph.children.first().children).hasSize(1)
            val grepNode = program.graph.children.first().children.first().ref
            require(grepNode is Node.Filter)

            assertThat(grepNode.predicate).isEqualTo(Predicate.matches("Station Eleven"))
        }

        @Test
        fun `compiles inverted grep correctly`() {
            val source = "grep -v 'Station Eleven' /dev/kafka/local/topics/books"

            val compiler = Compiler(session)
            val compilerResult = compiler.compile(source)

            assertThat(compilerResult.errors).isEmpty()

            val program = compilerResult.program

            assertThat(program.graph.children).hasSize(1)
            val streamSourceNode = program.graph.children.first().ref
            require(streamSourceNode is Node.StreamSource)

            assertThat(streamSourceNode.dataStream).isEqualTo(
                DataStream.fromAvroSchema("/dev/kafka/local/topics/books", AvroBook.`SCHEMA$`)
            )

            assertThat(program.graph.children.first().children).hasSize(1)
            val grepNode = program.graph.children.first().children.first().ref
            require(grepNode is Node.Filter)

            assertThat(grepNode.predicate).isEqualTo(Predicate.matches("Station Eleven").not())
        }

        @Test
        fun `compiles grep by key correctly`() {
            val source = "grep -k 'Station Eleven' /dev/kafka/local/topics/books"

            val compiler = Compiler(session)
            val compilerResult = compiler.compile(source)

            assertThat(compilerResult.errors).isEmpty()

            val program = compilerResult.program

            assertThat(program.graph.children).hasSize(1)
            val streamSourceNode = program.graph.children.first().ref
            require(streamSourceNode is Node.StreamSource)

            assertThat(streamSourceNode.dataStream).isEqualTo(
                DataStream.fromAvroSchema("/dev/kafka/local/topics/books", AvroBook.`SCHEMA$`)
            )

            assertThat(program.graph.children.first().children).hasSize(1)
            val grepNode = program.graph.children.first().children.first().ref
            require(grepNode is Node.Filter)

            assertThat(grepNode.predicate).isEqualTo(Predicate.matches("Station Eleven"))
            assertThat(grepNode.byKey).isTrue
        }

        @Test
        fun `compiles grep with predicate correctly`() {
            val source = "grep [.title ~= 'the'] /dev/kafka/local/topics/books"

            val compiler = Compiler(session)
            val compilerResult = compiler.compile(source)

            assertThat(compilerResult.errors).isEmpty()

            val program = compilerResult.program

            assertThat(program.graph.children).hasSize(1)
            val streamSourceNode = program.graph.children.first().ref
            require(streamSourceNode is Node.StreamSource)

            assertThat(streamSourceNode.dataStream).isEqualTo(
                DataStream.fromAvroSchema("/dev/kafka/local/topics/books", AvroBook.`SCHEMA$`)
            )

            assertThat(program.graph.children.first().children).hasSize(1)
            val grepNode = program.graph.children.first().children.first().ref
            require(grepNode is Node.Filter)

            assertThat(grepNode.predicate).isEqualTo(Predicate.almostEquals("title", Schema.String("the")))
        }
    }
}
