package io.typestream.compiler.types

import io.typestream.compiler.ast.Cat
import io.typestream.compiler.ast.Cut
import io.typestream.compiler.ast.Enrich
import io.typestream.compiler.ast.Expr
import io.typestream.compiler.ast.Grep
import io.typestream.compiler.ast.Join
import io.typestream.compiler.ast.Pipeline
import io.typestream.compiler.ast.ShellCommand
import io.typestream.compiler.lexer.Token
import io.typestream.compiler.lexer.TokenType
import io.typestream.compiler.types.datastream.fromAvroSchema
import io.typestream.compiler.types.datastream.join
import io.typestream.compiler.types.schema.Schema
import io.typestream.testing.avro.Author
import io.typestream.testing.avro.Book
import io.typestream.testing.avro.PageView
import io.typestream.testing.avro.Rating
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

internal class InferTest {
    @Nested
    inner class InferDataStream {
        @Test
        fun `infers type`() {
            val grep = Grep(listOf(Expr.BareWord("/dev/kafka/local/topics/authors")))
            grep.dataStreams.add(
                DataStream.fromAvroSchema("/dev/kafka/local/topics/authors", Author.getClassSchema())
            )

            assertThat(inferType(listOf(grep))).isEqualTo(
                DataStream.fromAvroSchema("/dev/kafka/local/topics/authors", Author.getClassSchema())
            )
        }

        @Test
        fun `infers joined type`() {
            val join = Join(
                listOf(
                    Expr.BareWord("/dev/kafka/local/topics/books"),
                    Expr.BareWord("/dev/kafka/local/topics/ratings")
                )
            )

            val books = DataStream.fromAvroSchema("/dev/kafka/local/topics/books", Book.getClassSchema())
            val ratings = DataStream.fromAvroSchema("/dev/kafka/local/topics/ratings", Rating.getClassSchema())

            join.dataStreams.add(books)
            join.dataStreams.add(ratings)

            assertThat(inferType(listOf(join))).isEqualTo(books.join(ratings))
        }
    }

    @Nested
    inner class InferPipeline {
        @Test
        fun `infers type`() {
            val cat = Cat(listOf(Expr.BareWord("/dev/kafka/local/topics/authors")))
            cat.dataStreams.add(DataStream.fromAvroSchema("/dev/kafka/local/topics/authors", Author.getClassSchema()))
            val grep = Grep(listOf(Expr.BareWord("Mandel")))

            val typeStream = inferType(listOf(cat, grep))

            assertThat(typeStream).isEqualTo(
                DataStream.fromAvroSchema("/dev/kafka/local/topics/authors", Author.getClassSchema())
            )
        }

        @Test
        fun `infers cut type`() {
            val cat = Cat(listOf(Expr.BareWord("/dev/kafka/local/topics/authors")))

            cat.dataStreams.add(DataStream.fromAvroSchema("/dev/kafka/local/topics/authors", Author.getClassSchema()))

            val cut = Cut(listOf(Expr.BareWord("name")))
            cut.boundArgs.add("name")

            val typeStream = inferType(listOf(cat, cut))

            val schema = Schema.Struct(listOf(Schema.Field("name", Schema.String.zeroValue)))

            assertThat(typeStream).isEqualTo(DataStream("/dev/kafka/local/topics/authors", schema))
        }
    }

    @Nested
    inner class EnrichPipeline {
        @Test
        fun `infers enrich type`() {
            val cat = Cat(listOf(Expr.BareWord("/dev/kafka/local/topics/page_views")))
            cat.dataStreams.add(DataStream.fromAvroSchema("/dev/kafka/local/topics/page_views", PageView.getClassSchema()))

            val cut = Cut(listOf(Expr.BareWord(".country")))
            cut.boundArgs.add("country")

            val enrich = Enrich(
                listOf(
                    Expr.Block(
                        Token(TokenType.ENRICH, "enrich", 0, 0),
                        Pipeline(
                            listOf(
                                ShellCommand(Token(TokenType.BAREWORD, "http", 0, 0), listOf()),
                                cut
                            )
                        )
                    )
                )
            )

            val grep = Grep(listOf(Expr.BareWord("US")))

            val typeStream = inferType(listOf(cat, enrich, grep))

            assertThat(typeStream).isEqualTo(
                DataStream(
                    "/dev/kafka/local/topics/page_views_http_cut", Schema.Struct(
                        listOf(
                            Schema.Field("book_id", Schema.UUID.zeroValue),
                            Schema.Field("ip_address", Schema.String.zeroValue),
                            Schema.Field("viewed_at", Schema.Instant.zeroValue(Schema.Instant.Precision.MILLIS)),
                            Schema.Field("country", Schema.String.zeroValue)
                        )
                    )
                )
            )
        }
    }
}
