package io.typestream.compiler.types.value

import io.typestream.compiler.ast.Predicate
import io.typestream.compiler.lexer.TokenType
import io.typestream.compiler.lexer.TokenType.*
import io.typestream.compiler.types.Value
import io.typestream.compiler.types.schema.Schema

fun Value.Companion.fromBinary(left: Value, operator: TokenType, right: Value): Value {
    if (left is Value.String && right is Value.String) {
        return when (operator) {
            PLUS -> Value.String(left.value + right.value)
            else -> error("$operator not supported")
        }
    }

    if (left is Value.Predicate && right is Value.Predicate) {
        return when (operator) {
            AND -> Value.Predicate(left.value.and(right.value))
            OR -> Value.Predicate(left.value.or(right.value))
            else -> error("$operator not supported")
        }
    }

    if (left is Value.FieldAccess && right is Value.String) {
        return fieldAccessAndString(left, operator, right)
    }

    if (left is Value.String && right is Value.FieldAccess) {
        return fieldAccessAndString(right, operator, left)
    }

    if (left is Value.FieldAccess && right is Value.Number) {
        return fieldAccessAndNumber(left, operator, right)
    }

    if (left is Value.Number && right is Value.FieldAccess) {
        return fieldAccessAndNumber(right, operator, left)
    }

    error("cannot apply $operator to $left and $right")
}

private fun fieldAccessAndString(left: Value.FieldAccess, operator: TokenType, right: Value.String) = when (operator) {
    EQUAL_EQUAL -> Value.Predicate(Predicate.equals(left.value, Schema.String(right.value)))
    BANG_EQUAL -> Value.Predicate(Predicate.equals(left.value, Schema.String(right.value)).not())
    ALMOST_EQUAL -> Value.Predicate(Predicate.almostEquals(left.value, Schema.String(right.value)))

    else -> error("$operator not supported")
}

private fun fieldAccessAndNumber(left: Value.FieldAccess, operator: TokenType, right: Value.Number) = when (operator) {
    BANG_EQUAL -> Value.Predicate(Predicate.equals(left.value, Schema.Long(right.value.toLong())).not())
    EQUAL_EQUAL -> Value.Predicate(Predicate.equals(left.value, Schema.Long(right.value.toLong())))
    GREATER -> Value.Predicate(Predicate.greaterThan(left.value, Schema.Long(right.value.toLong())))
    GREATER_EQUAL -> Value.Predicate(Predicate.greaterOrEqualThan(left.value, Schema.Long(right.value.toLong())))
    LESS -> Value.Predicate(Predicate.lessThan(left.value, Schema.Long(right.value.toLong())))
    LESS_EQUAL -> Value.Predicate(Predicate.lessOrEqualThan(left.value, Schema.Long(right.value.toLong())))

    else -> error("$operator not supported")
}

