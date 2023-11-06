package io.typestream.compiler.types.schema

import io.typestream.serializer.UUIDSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject

@Serializable
sealed interface Schema {
    val value: Any

    @Serializable
    data class Int(override val value: kotlin.Int) : Schema

    @Serializable
    data class List(override val value: kotlin.collections.List<Schema>) : Schema

    @Serializable
    data class Long(override val value: kotlin.Long) : Schema

    @Serializable
    data class Named(val name: kotlin.String, override val value: Schema) : Schema

    @Serializable
    data class String(override val value: kotlin.String) : Schema {
        companion object {
            val empty = String("")
        }

        override fun toString() = value
    }

    @Serializable
    data class Struct(override val value: kotlin.collections.List<Named>) : Schema

    @Serializable
    data class UUID(@Serializable(with = UUIDSerializer::class) override val value: java.util.UUID) : Schema

    fun canCompare(b: Schema): Boolean {
        return when (this) {
            is Int, is Long -> b is Int || b is Long
            is String -> b is String
            is UUID -> b is UUID
            else -> false
        }
    }

    fun toJsonElement(): JsonElement {
        return when (this) {
            is Int -> JsonPrimitive(value)
            is List -> buildJsonArray { value.forEach { schema -> add(schema.toJsonElement()) } }
            is Long -> JsonPrimitive(value)
            is Named -> value.toJsonElement()
            is String -> JsonPrimitive(value)
            is Struct -> buildJsonObject {
                value.forEach { namedValue ->
                    put(namedValue.name, namedValue.toJsonElement())
                }
            }

            is UUID -> JsonPrimitive(value.toString())
        }
    }

    fun select(fields: kotlin.collections.List<kotlin.String>): Schema {
        return when (this) {
            is Struct -> {
                val values = flatten().value.filter { fields.contains(it.name) }

                Struct(values).nest()
            }

            else -> error("cannot select from $this")
        }
    }

    fun selectOne(field: kotlin.String): Schema? {
        return when (this) {
            is Struct -> flatten().value.find { it.name == field }?.value
            else -> error("cannot selectOne from $this")
        }
    }

    fun prettyPrint(): kotlin.String {
        return when (this) {
            is Int -> value.toString()
            is List -> value.joinToString(", ", "[", "]") { it.prettyPrint() }
            is Long -> value.toString()
            is Named -> name + ": \"${value.prettyPrint()}\""
            is String -> value
            is Struct -> value.joinToString(", ", "{", "}") { it.prettyPrint() }
            is UUID -> value.toString()
        }
    }

    fun matches(pattern: kotlin.String): Boolean {
        return when (this) {
            is Int -> value.toString().contains(pattern)
            is List -> value.any { it.matches(pattern) }
            is Long -> value.toString().contains(pattern)
            is Named -> value.matches(pattern)
            is String -> value.contains(pattern, true)
            is Struct -> value.any { it.matches(pattern) }
            is UUID -> value.toString().contains(pattern)
        }
    }

    fun matches(other: Schema) = matches(other.value.toString())

    operator fun compareTo(other: Schema): kotlin.Int {
        return when (this) {
            is String -> value.compareTo(other.value.toString())
            is UUID -> value.compareTo(java.util.UUID.fromString(other.value.toString()))
            is Struct -> 0 // TODO what's the best way to compare two structs?
            is Named -> value.compareTo(value)
            is Int -> value.compareTo(other.value.toString().toInt())
            is Long -> value.compareTo(other.value.toString().toLong())
            is List -> 0 // TODO what's the best way to compare two lists?
        }
    }

    fun merge(schema: Schema): Schema {
        return when (this) {
            is Struct -> {
                require(schema is Struct) { "cannot merge $this with $schema" }

                val values = flatten().value + schema.flatten().value

                val newSchema = Struct(values)

                if (values.size > 1) {
                    newSchema.nest()
                } else {
                    newSchema
                }
            }

            else -> error("cannot merge $this")
        }
    }

    fun printTypes(): kotlin.String {
        return when (this) {
            is Int -> "Int"
            is List -> "List<${value.firstOrNull()?.printTypes() ?: "Any"}>"
            is Long -> "Long"
            is Named -> "Named<$name, ${value.printTypes()}>"
            is String -> "String"
            is Struct -> "Struct<${value.joinToString(", ") { "${it.name}: ${it.value.printTypes()}" }}>"
            is UUID -> "UUID"
        }
    }
}
