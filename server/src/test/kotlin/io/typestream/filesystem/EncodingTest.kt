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
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers


@Testcontainers
class EncodingTest {
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
    }

    @Test
    fun `infers avro encoding`() {
        val dataCommand = Cat(listOf(Expr.BareWord("/dev/kafka/local/topics/authors")))

        dataCommand.dataStreams.add(author())

        Assertions.assertThat(fileSystem.inferEncoding(dataCommand)).isEqualTo(Encoding.AVRO)
    }

    @Test
    fun `infers proto encoding`() {
        val dataCommand = Cat(listOf(Expr.BareWord("/dev/kafka/local/topics/books")))

        dataCommand.dataStreams.add(book(title = "Parable of the Sower"))

        Assertions.assertThat(fileSystem.inferEncoding(dataCommand)).isEqualTo(Encoding.PROTOBUF)
    }

    @Test
    fun `infers pipeline encoding`() {
        val cat = Cat(listOf(Expr.BareWord("/dev/kafka/local/topics/authors")))

        cat.dataStreams.add(author())

        val grep = Grep(listOf(Expr.BareWord("Butler")))

        val pipeline = Pipeline(listOf(cat, grep))

        Assertions.assertThat(fileSystem.inferEncoding(pipeline)).isEqualTo(Encoding.AVRO)
    }


    @Test
    fun `infers mixed pipeline encoding`() {
        val cat = Cat(listOf(Expr.BareWord("/dev/kafka/local/topics/authors")))

        cat.dataStreams.add(author())

        val join = Join(listOf(Expr.BareWord("/dev/kafka/local/topics/books")))

        join.dataStreams.add(book(title = "Parable of the Sower"))

        val pipeline = Pipeline(listOf(cat, join))

        Assertions.assertThat(fileSystem.inferEncoding(pipeline)).isEqualTo(Encoding.JSON)
    }
}

