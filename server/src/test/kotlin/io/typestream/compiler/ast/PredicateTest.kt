package io.typestream.compiler.ast

import io.typestream.compiler.types.schema.Schema
import io.typestream.helpers.book
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test


internal class PredicateTest {
    val books = listOf(
        book(title = "Station Eleven", wordCount = 300),
        book(title = "Kindred", wordCount = 400),
        book(title = "Parable of the Sower", wordCount = 250),
        book(title = "The Glass Hotel", wordCount = 300),
    )

    @Nested
    inner class Equals {

        @Test
        fun `matches string`() {
            val predicate = Predicate.equals("title", Schema.String("Station Eleven"))

            val filtered = books.filter { predicate.matches(it) }

            assertThat(filtered).hasSize(1)
            assertThat(filtered.first()["title"]).isEqualTo(Schema.String("Station Eleven"))
        }

        @Test
        fun `matches number equality`() {
            val predicate = Predicate.equals("word_count", Schema.Long(300))

            val filtered = books.filter { predicate.matches(it) }

            assertThat(filtered).hasSize(2)
            assertThat(filtered.map { it["title"] }).containsExactlyInAnyOrder(
                Schema.String("Station Eleven"),
                Schema.String("The Glass Hotel"),
            )
        }

        @Test
        fun `matches strings inequality`() {
            val predicate = Predicate.equals("title", Schema.String("Station Eleven")).not()

            val filtered = books.filter { predicate.matches(it) }

            assertThat(filtered).hasSize(3)
            assertThat(filtered.map { it["title"] }).containsExactlyInAnyOrder(
                Schema.String("Kindred"),
                Schema.String("Parable of the Sower"),
                Schema.String("The Glass Hotel"),
            )
        }
    }

    @Nested
    inner class Matches {
        @Test
        fun `matches strings`() {
            val predicate = Predicate.matches("hotel")

            val filtered = books.filter { predicate.matches(it) }

            assertThat(filtered).hasSize(1)
            assertThat(filtered.first()["title"]).isEqualTo(Schema.String("The Glass Hotel"))
        }

        @Test
        fun `negates strings match`() {
            val predicate = Predicate.matches("hotel").not()

            val filtered = books.filter { predicate.matches(it) }

            assertThat(filtered).hasSize(3)
            assertThat(filtered.map { it["title"] }).containsExactlyInAnyOrder(
                Schema.String("Station Eleven"),
                Schema.String("Kindred"),
                Schema.String("Parable of the Sower"),
            )
        }
    }
}
