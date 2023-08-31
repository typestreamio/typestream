package io.typestream.compiler.types.datastream

import io.typestream.compiler.types.DataStream
import io.typestream.compiler.types.schema.Schema
import io.typestream.helpers.book
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test


class JoinExtTest {

    @Test
    fun `joins two data streams`() {
        val a = book(title = "Station Eleven")

        val b = DataStream(
            "/dev/kafka/local/topics/ratings",
            Schema.Struct(
                listOf(
                    Schema.Named("book_id", Schema.String.empty),
                    Schema.Named("rating", Schema.Int(42))
                )
            )
        )

        val result = a.join(b)

        assertThat(result)
            .extracting("path", "schema")
            .containsExactly(
                "/dev/kafka/local/topics/books_ratings",
                Schema.Struct(
                    listOf(
                        Schema.Named("books", a.schema),
                        Schema.Named("ratings", b.schema)
                    )
                )
            )
    }

    @Test
    fun `reuses path if it's the same`() {
        val a = DataStream(
            "/bin/ls",
            Schema.Struct(
                listOf(
                    Schema.Named("a", Schema.String.empty),
                    Schema.Named("b", Schema.String.empty)
                )
            )
        )

        val b = DataStream(
            "/bin/ls",
            Schema.Struct(
                listOf(
                    Schema.Named("c", Schema.String.empty),
                    Schema.Named("d", Schema.String.empty)
                )
            )
        )

        val result = a.join(b)

        assertThat(result)
            .extracting("path", "schema")
            .containsExactly(
                "/bin/ls",
                Schema.Struct(
                    listOf(
                        Schema.Named("ls", a.schema),
                        Schema.Named("ls", b.schema),
                    )
                )
            )
    }
}
