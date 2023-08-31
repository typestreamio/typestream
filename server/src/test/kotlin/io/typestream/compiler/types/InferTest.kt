package io.typestream.compiler.types

import io.typestream.compiler.ast.Cat
import io.typestream.compiler.ast.Cut
import io.typestream.compiler.ast.Expr
import io.typestream.compiler.ast.Grep
import io.typestream.compiler.ast.Join
import io.typestream.compiler.types.datastream.fromAvroSchema
import io.typestream.compiler.types.datastream.join
import io.typestream.compiler.types.schema.Schema
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

internal class InferTest {

    @Nested
    inner class InferDataStream {
        @Test
        fun `infers type correctly`() {
            val grep = Grep(listOf(Expr.BareWord("/dev/kafka/local/topics/authors")))
            grep.dataStreams.add(
                DataStream.fromAvroSchema(
                    "/dev/kafka/local/topics/authors",
                    io.typestream.testing.avro.Author.`SCHEMA$`
                )
            )

            assertThat(grep.inferType()).isEqualTo(
                DataStream.fromAvroSchema(
                    "/dev/kafka/local/topics/authors",
                    io.typestream.testing.avro.Author.`SCHEMA$`
                )
            )
        }

        @Test
        fun `infers joined type correctly`() {
            val join = Join(
                listOf(
                    Expr.BareWord("/dev/kafka/local/topics/books"),
                    Expr.BareWord("/dev/kafka/local/topics/ratings")
                )
            )

            val books =
                DataStream.fromAvroSchema("/dev/kafka/local/topics/books", io.typestream.testing.avro.Book.`SCHEMA$`)
            val ratings = DataStream.fromAvroSchema(
                "/dev/kafka/local/topics/ratings",
                io.typestream.testing.avro.Rating.`SCHEMA$`
            )

            join.dataStreams.add(books)
            join.dataStreams.add(ratings)

            assertThat(join.inferType()).isEqualTo(books.join(ratings))
        }
    }

    @Nested
    inner class InferPipeline {
        @Test
        fun `infers type correctly`() {
            val cat = Cat(listOf(Expr.BareWord("/dev/kafka/local/topics/authors")))
            cat.dataStreams.add(
                DataStream.fromAvroSchema(
                    "/dev/kafka/local/topics/authors",
                    io.typestream.testing.avro.Author.`SCHEMA$`
                )
            )
            val grep = Grep(listOf(Expr.BareWord("Mandel")))

            val typeStream = inferType(listOf(cat, grep))

            assertThat(typeStream).isEqualTo(
                DataStream.fromAvroSchema(
                    "/dev/kafka/local/topics/authors",
                    io.typestream.testing.avro.Author.`SCHEMA$`
                )
            )
        }

        @Test
        fun `infers cut type correctly`() {
            val cat = Cat(listOf(Expr.BareWord("/dev/kafka/local/topics/authors")))

            cat.dataStreams.add(
                DataStream.fromAvroSchema(
                    "/dev/kafka/local/topics/authors",
                    io.typestream.testing.avro.Author.`SCHEMA$`
                )
            )

            val cut = Cut(listOf(Expr.BareWord("name")))
            cut.boundArgs.add("name")

            val typeStream = inferType(listOf(cat, cut))

            val schema = Schema.Struct(listOf(Schema.Named("name", Schema.String.empty)))

            assertThat(typeStream).isEqualTo(DataStream("/dev/kafka/local/topics/authors", schema))
        }
    }
}
