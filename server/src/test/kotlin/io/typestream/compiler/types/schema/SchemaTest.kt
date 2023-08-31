package io.typestream.compiler.types.schema

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

internal class SchemaTest {

    @Nested
    inner class Match {

        @Test
        fun `matches a string`() {
            val value = Schema.String("the answer is 42")

            assertThat(value.matches("42")).isTrue
            assertThat(value.matches("24")).isFalse
        }

        @Test
        fun `matches an int`() {
            val value = Schema.Int(42)

            assertThat(value.matches("42")).isTrue
            assertThat(value.matches("24")).isFalse
        }

        @Test
        fun `matches a long`() {
            val value = Schema.Long(42L)

            assertThat(value.matches("42")).isTrue
            assertThat(value.matches("24")).isFalse
        }

        @Test
        fun `matches a struct`() {
            val value = Schema.Struct(
                listOf(
                    Schema.Named("id", Schema.String("42")),
                    Schema.Named("name", Schema.String("Grace Hopper")),
                )
            )

            assertThat(value.matches("42")).isTrue
            assertThat(value.matches("Grace")).isTrue
            assertThat(value.matches("24")).isFalse
        }

        @Test
        fun `matches a named value`() {
            val value = Schema.Named("id", Schema.String("42"))

            assertThat(value.matches("42")).isTrue
            assertThat(value.matches("24")).isFalse
        }
    }

    @Nested
    inner class PrettyPrint {
        @Test
        fun `pretty prints strings`() {
            val value = Schema.String("the answer is 42")

            assertThat(value.prettyPrint()).isEqualTo("the answer is 42")
        }

        @Test
        fun `pretty prints int`() {
            val value = Schema.Int(42)

            assertThat(value.prettyPrint()).isEqualTo("42")
        }

        @Test
        fun `pretty prints structs`() {
            val value = Schema.Struct(listOf(Schema.Named("id", Schema.String("42"))))

            assertThat(value.prettyPrint()).isEqualTo("{id: \"42\"}")
        }

        @Test
        fun `pretty prints empty values for structs`() {
            val value = Schema.Struct(
                listOf(
                    Schema.Named("id", Schema.String("")),
                    Schema.Named("answer", Schema.Long(42L))
                )
            )

            assertThat(value.prettyPrint()).isEqualTo("{id: \"\", answer: \"42\"}")
        }
    }
}
