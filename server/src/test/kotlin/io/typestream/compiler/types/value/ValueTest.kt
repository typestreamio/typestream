package io.typestream.compiler.types.value

import io.typestream.compiler.ast.Predicate
import io.typestream.compiler.lexer.TokenType.BANG_EQUAL
import io.typestream.compiler.lexer.TokenType.EQUAL_EQUAL
import io.typestream.compiler.lexer.TokenType.GREATER
import io.typestream.compiler.lexer.TokenType.OR
import io.typestream.compiler.types.Value
import io.typestream.compiler.types.schema.Schema
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test


class ValueTest {

    @Test
    fun `handles grouped conditions`() {
        assertThat(
            Value.fromBinary(
                Value.Predicate(Predicate.equals("title", Schema.String("Station Eleven"))),
                OR,
                Value.Predicate(Predicate.equals("title", Schema.String("Kindred")))
            ).value
        ).isEqualTo(
            Predicate.equals("title", Schema.String("Station Eleven"))
                .or(Predicate.equals("title", Schema.String("Kindred")))
        )
    }

    @Test
    fun `handles simple condition`() {
        assertThat(
            Value.fromBinary(
                Value.FieldAccess("title"),
                EQUAL_EQUAL,
                Value.String("Station Eleven")
            ).value
        ).isEqualTo(Predicate.equals("title", Schema.String("Station Eleven")))
    }

    @Test
    fun `handles simple condition with not`() {
        assertThat(
            Value.fromBinary(
                Value.FieldAccess("title"),
                BANG_EQUAL,
                Value.String("Station Eleven")
            ).value
        ).isEqualTo(Predicate.equals("title", Schema.String("Station Eleven")).not())
    }

    @Test
    fun `handles number condition`() {
        assertThat(
            Value.fromBinary(
                Value.FieldAccess("year"),
                GREATER,
                Value.Number(2010.0)
            ).value
        ).isEqualTo(Predicate.greaterThan("year", Schema.Long(2010)))
    }
}
