package io.typestream.compiler.types.schema

import org.assertj.core.api.Assertions
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test


internal class SchemaStructTest {

    private val author = Schema.Struct(
        listOf(
            Schema.Named("id", Schema.String("42")),
            Schema.Named("name", Schema.String("Emily St John Mandel")),
            Schema.Named(
                "book", Schema.Struct(
                    listOf(
                        Schema.Named("title", Schema.String("Station Eleven")),
                        Schema.Named("wordCount", Schema.Int(42)),
                        Schema.Named(
                            "publisher", Schema.Struct(
                                listOf(
                                    Schema.Named("name", Schema.String("Knopf")),
                                    Schema.Named(
                                        "address", Schema.Struct(
                                            listOf(
                                                Schema.Named("city", Schema.String("New York City")),
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
            Schema.Named("id", Schema.String("42")),
            Schema.Named("name", Schema.String("Emily St John Mandel")),
            Schema.Named("book.title", Schema.String("Station Eleven")),
            Schema.Named("book.wordCount", Schema.Int(42)),
            Schema.Named("book.publisher.name", Schema.String("Knopf")),
            Schema.Named("book.publisher.address.city", Schema.String("New York City")),
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
                    Schema.Named("id", Schema.String("42")),
                    Schema.Named("name", Schema.String("Grace Hopper")),
                )
            )

            val selection = struct.select(listOf("id"))

            require(selection is Schema.Struct)
            assertThat(selection.value)
                .hasSize(1)
                .extracting("name", "value")
                .containsExactly(Assertions.tuple("id", Schema.String("42")))
        }

        @Test
        fun `selects nested fields`() {
            val struct = Schema.Struct(
                listOf(
                    Schema.Named("id", Schema.String("42")),
                    Schema.Named("name", Schema.String("Emily St John Mandel")),
                    Schema.Named(
                        "book", Schema.Struct(
                            listOf(
                                Schema.Named("title", Schema.String("Station Eleven")),
                                Schema.Named("wordCount", Schema.Int(42)),
                                Schema.Named(
                                    "publisher", Schema.Struct(
                                        listOf(
                                            Schema.Named("name", Schema.String("Knopf")),
                                            Schema.Named(
                                                "address", Schema.Struct(
                                                    listOf(
                                                        Schema.Named("city", Schema.String("New York City")),
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
                    Assertions.tuple("id", Schema.String("42")),
                    Assertions.tuple("name", Schema.String("Emily St John Mandel")),
                    Assertions.tuple(
                        "book", Schema.Struct(
                            listOf(
                                Schema.Named("title", Schema.String("Station Eleven")),
                                Schema.Named(
                                    "publisher", Schema.Struct(
                                        listOf(
                                            Schema.Named("name", Schema.String("Knopf")),
                                            Schema.Named(
                                                "address", Schema.Struct(
                                                    listOf(
                                                        Schema.Named("city", Schema.String("New York City")),
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
