package io.typestream.compiler.ast

import io.typestream.compiler.types.DataStream
import io.typestream.compiler.types.schema.Schema
import kotlinx.serialization.Serializable

@Serializable
sealed interface Predicate {
    fun matches(dataStream: DataStream): Boolean

    private data class And(val left: Predicate, val right: Predicate) : Predicate {
        override fun matches(dataStream: DataStream) = left.matches(dataStream) && right.matches(dataStream)
    }

    private data class Or(val left: Predicate, val right: Predicate) : Predicate {
        override fun matches(dataStream: DataStream) = left.matches(dataStream) || right.matches(dataStream)
    }

    private data class Not(val predicate: Predicate) : Predicate {
        override fun matches(dataStream: DataStream) = !predicate.matches(dataStream)
    }

    private data class GreaterThan(val key: String, val value: Schema) : Predicate {
        override fun matches(dataStream: DataStream) = dataStream[key] > value
    }

    private data class GreaterOrEqualThan(val key: String, val value: Schema) : Predicate {
        override fun matches(dataStream: DataStream) = dataStream[key] >= value
    }

    private data class LessThan(val key: String, val value: Schema) : Predicate {
        override fun matches(dataStream: DataStream) = dataStream[key] < value
    }

    private data class LessOrEqualThan(val key: String, val value: Schema) : Predicate {
        override fun matches(dataStream: DataStream) = dataStream[key] <= value
    }

    private data class Equals(val key: String, val value: Schema) : Predicate {
        override fun matches(dataStream: DataStream) = dataStream[key].compareTo(value) == 0
    }

    private data class Matches(val pattern: String) : Predicate {
        override fun matches(dataStream: DataStream) = dataStream.matches(pattern)
    }

    private data class AlmostEquals(val key: String, val value: Schema) : Predicate {
        override fun matches(dataStream: DataStream) = dataStream[key].matches(value)
    }

    fun and(other: Predicate): Predicate = And(this, other)
    fun or(other: Predicate): Predicate = Or(this, other)
    fun not(): Predicate = Not(this)

    fun typeCheck(dataStream: DataStream): List<String> {
        return when (this) {
            is And -> left.typeCheck(dataStream) + right.typeCheck(dataStream)
            is Or -> left.typeCheck(dataStream) + right.typeCheck(dataStream)
            is Not -> predicate.typeCheck(dataStream)
            is GreaterThan -> opTypeCheck(dataStream, key, value)
            is GreaterOrEqualThan -> opTypeCheck(dataStream, key, value)
            is LessThan -> opTypeCheck(dataStream, key, value)
            is LessOrEqualThan -> opTypeCheck(dataStream, key, value)
            is Equals -> opTypeCheck(dataStream, key, value)
            is AlmostEquals -> opTypeCheck(dataStream, key, value)
            is Matches -> listOf()
        }
    }

    private fun opTypeCheck(dataStream: DataStream, key: String, value: Schema) = buildList {
        if (!dataStream.hasField(key)) {
            add("cannot find field '$key' in ${dataStream.path}.\nYou can use 'file ${dataStream.path}' to check available fields")
        } else {
            val schema = dataStream[key]
            if (!schema.canCompare(value)) {
                add("cannot compare field '$key' of type ${schema::class.simpleName} with ${value::class.simpleName}")
            }
        }
    }

    companion object {
        fun equals(key: String, value: Schema): Predicate = Equals(key, value)
        fun almostEquals(key: String, value: Schema): Predicate = AlmostEquals(key, value)
        fun matches(pattern: String): Predicate = Matches(pattern)
        fun greaterThan(key: String, value: Schema): Predicate = GreaterThan(key, value)
        fun greaterOrEqualThan(key: String, value: Schema): Predicate = GreaterOrEqualThan(key, value)
        fun lessThan(key: String, value: Schema): Predicate = LessThan(key, value)
        fun lessOrEqualThan(key: String, value: Schema): Predicate = LessOrEqualThan(key, value)
    }
}
