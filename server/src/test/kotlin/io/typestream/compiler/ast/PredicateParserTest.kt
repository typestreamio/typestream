package io.typestream.compiler.ast

import io.typestream.compiler.types.schema.Schema
import io.typestream.helpers.book
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test


internal class PredicateParserTest {
    private val books = listOf(
        book(title = "Station Eleven", wordCount = 300),
        book(title = "Kindred", wordCount = 400),
        book(title = "Parable of the Sower", wordCount = 250),
        book(title = "The Glass Hotel", wordCount = 300),
    )

    @Nested
    inner class StringEquality {
        @Test
        fun `parses string equality with double quotes`() {
            val predicate = PredicateParser.parse(".title == \"Station Eleven\"")

            val filtered = books.filter { predicate.matches(it) }

            assertThat(filtered).hasSize(1)
            assertThat(filtered.first()["title"]).isEqualTo(Schema.String("Station Eleven"))
        }

        @Test
        fun `parses string equality with single quotes`() {
            val predicate = PredicateParser.parse(".title == 'Kindred'")

            val filtered = books.filter { predicate.matches(it) }

            assertThat(filtered).hasSize(1)
            assertThat(filtered.first()["title"]).isEqualTo(Schema.String("Kindred"))
        }

        @Test
        fun `parses string inequality`() {
            val predicate = PredicateParser.parse(".title != \"Station Eleven\"")

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
    inner class NumericComparison {
        @Test
        fun `parses numeric equality`() {
            val predicate = PredicateParser.parse(".word_count == 300")

            val filtered = books.filter { predicate.matches(it) }

            assertThat(filtered).hasSize(2)
            assertThat(filtered.map { it["title"] }).containsExactlyInAnyOrder(
                Schema.String("Station Eleven"),
                Schema.String("The Glass Hotel"),
            )
        }

        @Test
        fun `parses greater than`() {
            val predicate = PredicateParser.parse(".word_count > 300")

            val filtered = books.filter { predicate.matches(it) }

            assertThat(filtered).hasSize(1)
            assertThat(filtered.first()["title"]).isEqualTo(Schema.String("Kindred"))
        }

        @Test
        fun `parses greater or equal`() {
            val predicate = PredicateParser.parse(".word_count >= 300")

            val filtered = books.filter { predicate.matches(it) }

            assertThat(filtered).hasSize(3)
        }

        @Test
        fun `parses less than`() {
            val predicate = PredicateParser.parse(".word_count < 300")

            val filtered = books.filter { predicate.matches(it) }

            assertThat(filtered).hasSize(1)
            assertThat(filtered.first()["title"]).isEqualTo(Schema.String("Parable of the Sower"))
        }

        @Test
        fun `parses less or equal`() {
            val predicate = PredicateParser.parse(".word_count <= 300")

            val filtered = books.filter { predicate.matches(it) }

            assertThat(filtered).hasSize(3)
        }
    }

    @Nested
    inner class LogicalOperators {
        @Test
        fun `parses AND expressions`() {
            val predicate = PredicateParser.parse("(.word_count >= 300) && (.word_count <= 300)")

            val filtered = books.filter { predicate.matches(it) }

            assertThat(filtered).hasSize(2)
            assertThat(filtered.map { it["title"] }).containsExactlyInAnyOrder(
                Schema.String("Station Eleven"),
                Schema.String("The Glass Hotel"),
            )
        }

        @Test
        fun `parses OR expressions`() {
            val predicate = PredicateParser.parse("(.title == \"Kindred\") || (.title == \"Station Eleven\")")

            val filtered = books.filter { predicate.matches(it) }

            assertThat(filtered).hasSize(2)
            assertThat(filtered.map { it["title"] }).containsExactlyInAnyOrder(
                Schema.String("Kindred"),
                Schema.String("Station Eleven"),
            )
        }

        @Test
        fun `parses NOT expressions`() {
            val predicate = PredicateParser.parse("!(.word_count == 300)")

            val filtered = books.filter { predicate.matches(it) }

            assertThat(filtered).hasSize(2)
            assertThat(filtered.map { it["title"] }).containsExactlyInAnyOrder(
                Schema.String("Kindred"),
                Schema.String("Parable of the Sower"),
            )
        }
    }

    @Nested
    inner class EdgeCases {
        @Test
        fun `falls back to regex for unstructured patterns`() {
            val predicate = PredicateParser.parse("hotel")

            val filtered = books.filter { predicate.matches(it) }

            assertThat(filtered).hasSize(1)
            assertThat(filtered.first()["title"]).isEqualTo(Schema.String("The Glass Hotel"))
        }

        @Test
        fun `empty expression matches everything`() {
            val predicate = PredicateParser.parse("")

            val filtered = books.filter { predicate.matches(it) }

            assertThat(filtered).hasSize(4)
        }

        @Test
        fun `handles underscore field names`() {
            val predicate = PredicateParser.parse(".word_count > 250")

            val filtered = books.filter { predicate.matches(it) }

            assertThat(filtered).hasSize(3)
        }
    }
}
