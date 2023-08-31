package io.typestream.compiler.types

import kotlinx.serialization.Serializable

@Serializable
sealed interface Value {
    val value: Any

    @Serializable
    data class FieldAccess(override val value: kotlin.String) : Value {
        override fun toString() = value
    }

    @Serializable
    data class Number(override val value: Double) : Value {
        override fun toString() = value.toString()
    }

    @Serializable
    data class String(override val value: kotlin.String) : Value {
        override fun toString() = value
    }

    @Serializable
    data class List(override val value: kotlin.collections.List<Value>) : Value

    @Serializable
    data class Block(override val value: (DataStream) -> DataStream) : Value

    @Serializable
    data class Predicate(override val value: io.typestream.compiler.ast.Predicate) : Value

    companion object
}
