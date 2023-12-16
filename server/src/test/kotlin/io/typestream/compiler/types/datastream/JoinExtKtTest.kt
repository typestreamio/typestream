package io.typestream.compiler.types.datastream

import io.typestream.compiler.types.DataStream
import io.typestream.compiler.types.schema.Schema
import io.typestream.helpers.book
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test

internal class JoinExtKtTest {
    @Test
    fun `joins two data streams`() {
        val a = book(title = "Station Eleven")

        val b = DataStream(
            "/dev/kafka/local/topics/ratings",
            Schema.Struct(
                listOf(
                    Schema.Field("book_id", Schema.String.zeroValue),
                    Schema.Field("rating", Schema.Int(42))
                )
            )
        )

        val result = a.join(b)

        Assertions.assertThat(result)
            .extracting("path", "schema")
            .containsExactly(
                "/dev/kafka/local/topics/books_ratings",
                Schema.Struct(
                    listOf(Schema.Field("books", a.schema), Schema.Field("ratings", b.schema))
                )
            )
    }

    @Test
    fun `reuses path if it's the same`() {
        val a = DataStream(
            "/bin/ls",
            Schema.Struct(
                listOf(Schema.Field("a", Schema.String.zeroValue), Schema.Field("b", Schema.String.zeroValue))
            )
        )

        val b = DataStream(
            "/bin/ls",
            Schema.Struct(
                listOf(Schema.Field("c", Schema.String.zeroValue), Schema.Field("d", Schema.String.zeroValue))
            )
        )

        val result = a.join(b)

        Assertions.assertThat(result)
            .extracting("path", "schema")
            .containsExactly(
                "/bin/ls",
                Schema.Struct(listOf(Schema.Field("ls", a.schema), Schema.Field("ls", b.schema)))
            )
    }
}
