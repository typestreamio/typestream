package io.typestream.compiler.types.schema

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.tuple
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

internal class SchemaStructTest {
    private val author = Schema.Struct(
        listOf(
            Schema.Field("id", Schema.String("42")),
            Schema.Field("name", Schema.String("Emily St John Mandel")),
            Schema.Field(
                "book", Schema.Struct(
                    listOf(
                        Schema.Field("title", Schema.String("Station Eleven")),
                        Schema.Field("wordCount", Schema.Int(42)),
                        Schema.Field(
                            "publisher", Schema.Struct(
                                listOf(
                                    Schema.Field("name", Schema.String("Knopf")),
                                    Schema.Field(
                                        "address", Schema.Struct(
                                            listOf(
                                                Schema.Field("city", Schema.String("New York City")),
                                            )
                                        )
                                    ),
                                )
                            )
                        ),
                    )
                )
            )
        )
    )

    private val flattenedAuthor = Schema.Struct(
        listOf(
            Schema.Field("id", Schema.String("42")),
            Schema.Field("name", Schema.String("Emily St John Mandel")),
            Schema.Field("book.title", Schema.String("Station Eleven")),
            Schema.Field("book.wordCount", Schema.Int(42)),
            Schema.Field("book.publisher.name", Schema.String("Knopf")),
            Schema.Field("book.publisher.address.city", Schema.String("New York City")),
        )
    )

    @Test
    fun `flattens correctly`() {
        assertThat(author.flatten()).isEqualTo(flattenedAuthor)
    }

    @Test
    fun `nests correctly`() {
        assertThat(flattenedAuthor.nest()).isEqualTo(author)
    }

    @Nested
    inner class Select {

        @Test
        fun `selects top-level fields`() {
            val struct = Schema.Struct(
                listOf(
                    Schema.Field("id", Schema.String("42")),
                    Schema.Field("name", Schema.String("Grace Hopper")),
                )
            )

            val selection = struct.select(listOf("id"))

            require(selection is Schema.Struct)
            assertThat(selection.value)
                .hasSize(1)
                .extracting("name", "value")
                .containsExactly(tuple("id", Schema.String("42")))
        }


        @Test
        fun `selects nested fields`() {
            val struct = Schema.Struct(
                listOf(
                    Schema.Field("id", Schema.String("42")),
                    Schema.Field("name", Schema.String("Emily St John Mandel")),
                    Schema.Field(
                        "book", Schema.Struct(
                            listOf(
                                Schema.Field("title", Schema.String("Station Eleven")),
                                Schema.Field("wordCount", Schema.Int(42)),
                                Schema.Field(
                                    "publisher", Schema.Struct(
                                        listOf(
                                            Schema.Field("name", Schema.String("Knopf")),
                                            Schema.Field(
                                                "address", Schema.Struct(
                                                    listOf(
                                                        Schema.Field("city", Schema.String("New York City")),
                                                    )
                                                )
                                            ),
                                        )
                                    )
                                ),
                            )
                        )
                    )
                )
            )

            val selection =
                struct.select(listOf("id", "name", "book.title", "book.publisher.name", "book.publisher.address.city"))

            require(selection is Schema.Struct)
            assertThat(selection.value)
                .hasSize(3)
                .extracting("name", "value")
                .containsExactly(
                    tuple("id", Schema.String("42")),
                    tuple("name", Schema.String("Emily St John Mandel")),
                    tuple(
                        "book", Schema.Struct(
                            listOf(
                                Schema.Field("title", Schema.String("Station Eleven")),
                                Schema.Field(
                                    "publisher", Schema.Struct(
                                        listOf(
                                            Schema.Field("name", Schema.String("Knopf")),
                                            Schema.Field(
                                                "address", Schema.Struct(
                                                    listOf(
                                                        Schema.Field("city", Schema.String("New York City")),
                                                    )
                                                )
                                            )
                                        )
                                    )
                                )
                            )
                        )
                    )
                )
        }
    }

}
