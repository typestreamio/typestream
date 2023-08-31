package io.typestream.compiler.ast

import io.typestream.compiler.types.schema.Schema
import io.typestream.helpers.book
import org.junit.jupiter.api.Test
import kotlin.test.assertTrue


internal class PredicateTest {
    @Test
    fun `matches by equality`() {
        val predicate = Predicate.equals("title", Schema.String("Station Eleven"))
        val dataStream = book(title = "Station Eleven")

        assertTrue(predicate.matches(dataStream))
    }
}
