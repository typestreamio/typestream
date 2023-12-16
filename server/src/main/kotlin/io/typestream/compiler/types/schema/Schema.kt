package io.typestream.compiler.types.schema

import io.typestream.serializer.BigDecimalSerializer
import io.typestream.serializer.UUIDSerializer
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import java.math.BigDecimal

@Serializable
sealed interface Schema {
    val value: Any?

    @Serializable
    data class Boolean(override val value: kotlin.Boolean) : Schema {
        companion object {
            fun fromAnyValue(value: Any?): Boolean {
                return when (value) {
                    is kotlin.Boolean -> Boolean(value)
                    else -> Boolean(false)
                }
            }
        }
    }

    @Serializable
    data class Date(override val value: LocalDate) : Schema {
        companion object {
            fun fromAnyValue(value: Any?): Date {
                return when (value) {
                    is java.time.LocalDate -> Date(LocalDate(value.year, value.monthValue, value.dayOfMonth))
                    else -> Date(LocalDate.fromEpochDays(0))
                }
            }
        }
    }

    @Serializable
    data class DateTime(override val value: LocalDateTime, val precision: Precision) : Schema {
        enum class Precision {
            MICROS,
            MILLIS
        }

        companion object {
            fun zeroValue(precision: Precision) = DateTime(LocalDateTime(0, 1, 1, 0, 0, 0, 0), precision)
            fun fromAnyValue(value: Any?, precision: Precision): DateTime {
                return when (value) {
                    is kotlin.Long -> {
                        var mills = value

                        if (precision == Precision.MICROS) {
                            mills /= 1000
                        }

                        DateTime(
                            kotlinx.datetime.Instant.fromEpochMilliseconds(mills)
                                .toLocalDateTime(kotlinx.datetime.TimeZone.UTC), precision
                        )
                    }

                    is java.time.LocalDateTime -> DateTime(
                        LocalDateTime(
                            value.year,
                            value.monthValue,
                            value.dayOfMonth,
                            value.hour,
                            value.minute,
                            value.second,
                            value.nano
                        ), precision
                    )


                    else -> zeroValue(precision)
                }
            }
        }
    }

    @Serializable
    data class Decimal(@Serializable(with = BigDecimalSerializer::class) override val value: BigDecimal) : Schema {
        companion object {
            fun fromAnyValue(value: Any?): Decimal {
                return when (value) {
                    is BigDecimal -> Decimal(value)
                    else -> Decimal(BigDecimal.ZERO)
                }
            }
        }
    }

    @Serializable
    data class Double(override val value: kotlin.Double) : Schema {
        companion object {
            fun fromAnyValue(value: Any?): Double {
                return when (value) {
                    is kotlin.Double -> Double(value)
                    else -> Double(0.0)
                }
            }
        }
    }


    @Serializable
    data class Instant(override val value: kotlinx.datetime.Instant, val precision: Precision) : Schema {
        enum class Precision {
            MICROS,
            MILLIS
        }

        companion object {
            fun zeroValue(precision: Precision) =
                Instant(kotlinx.datetime.Instant.fromEpochMilliseconds(0), precision)

            fun fromAnyValue(value: Any?, precision: Precision): Instant {
                return when (value) {
                    is java.time.Instant -> Instant(
                        kotlinx.datetime.Instant.fromEpochSeconds(
                            value.epochSecond,
                            value.nano
                        ),
                        precision
                    )

                    else -> zeroValue(precision)
                }
            }
        }
    }

    @Serializable
    data class Int(override val value: kotlin.Int) : Schema {
        companion object {
            fun fromAnyValue(value: Any?): Int {
                return when (value) {
                    is kotlin.Int -> Int(value)
                    else -> Int(0)
                }
            }
        }
    }

    @Serializable
    data class Float(override val value: kotlin.Float) : Schema {
        companion object {
            fun fromAnyValue(value: Any?): Float {
                return when (value) {
                    is kotlin.Float -> Float(value)
                    else -> Float(0.0f)
                }
            }
        }
    }

    @Serializable
    data class Enum(override val value: kotlin.String, val symbols: kotlin.collections.List<kotlin.String>) : Schema

    @Serializable
    data class List(override val value: kotlin.collections.List<Schema>, val valueType: Schema) : Schema

    @Serializable
    data class Long(override val value: kotlin.Long) : Schema {
        companion object {
            fun fromAnyValue(value: Any?): Long {
                return when (value) {
                    is kotlin.Long -> Long(value)
                    else -> Long(0L)
                }
            }
        }
    }

    @Serializable
    data class Map(override val value: kotlin.collections.Map<kotlin.String, Schema>, val valueType: Schema) : Schema

    @Serializable
    data class String(override val value: kotlin.String) : Schema {
        companion object {
            val zeroValue = String("")

            fun fromAnyValue(value: Any?): String {
                if (value == null) {
                    return zeroValue
                }
                return String(value.toString())
            }
        }
    }

    @Serializable
    data class Optional(override val value: Schema?) : Schema

    @Serializable
    data class Field(val name: kotlin.String, val value: Schema)

    @Serializable
    data class Struct(override val value: kotlin.collections.List<Field>) : Schema {
        private val fieldsByName = value.associate { field -> field.name to field.value }

        operator fun get(key: kotlin.String): Schema = fieldsByName[key] ?: error("no such field: $key")
    }

    @Serializable
    data class Time(override val value: LocalTime, val precision: Precision) : Schema {
        enum class Precision {
            MICROS,
            MILLIS
        }

        companion object {
            fun zeroValue(precision: Precision) = Time(LocalTime.fromSecondOfDay(0), precision)

            fun fromAnyValue(value: Any?, precision: Precision): Time {
                return when (value) {
                    is kotlin.Int -> Time(LocalTime.fromMillisecondOfDay(value), precision)
                    is java.time.LocalTime -> Time(
                        LocalTime(value.hour, value.minute, value.second, value.nano),
                        precision
                    )

                    else -> zeroValue(precision)
                }
            }
        }
    }

    @Serializable
    data class UUID(@Serializable(with = UUIDSerializer::class) override val value: java.util.UUID) : Schema {
        companion object {
            val zeroValue: UUID = UUID(java.util.UUID.fromString("00000000-0000-0000-0000-000000000000"))

            fun fromAnyValue(value: Any?): UUID {
                return when (value) {
                    is java.util.UUID -> UUID(value)
                    else -> zeroValue
                }
            }
        }
    }

    fun canCompare(b: Schema): kotlin.Boolean {
        return when (this) {
            is Int, is Long -> b is Int || b is Long
            is String -> b is String
            is UUID -> b is UUID
            else -> false
        }
    }

    fun toJsonElement(): JsonElement {
        return when (this) {
            is Boolean -> JsonPrimitive(value)
            is Date -> JsonPrimitive(value.toString())
            is DateTime -> JsonPrimitive(value.toString())
            is Decimal -> JsonPrimitive(value)
            is Double -> JsonPrimitive(value)
            is Enum -> JsonPrimitive(value)
            is Float -> JsonPrimitive(value)
            is Instant -> JsonPrimitive(value.toString())
            is Int -> JsonPrimitive(value)
            is List -> buildJsonArray { value.forEach { schema -> add(schema.toJsonElement()) } }
            is Long -> JsonPrimitive(value)
            is Map -> buildJsonObject { value.forEach { (key, schema) -> put(key, schema.toJsonElement()) } }
            is Optional -> value?.toJsonElement() ?: JsonPrimitive("null")
            is String -> JsonPrimitive(value)
            is Struct -> buildJsonObject {
                value.forEach { field -> put(field.name, field.value.toJsonElement()) }
            }

            is Time -> JsonPrimitive(value.toString())


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
            is Boolean -> value.toString()
            is Double -> value.toString()
            is Date -> value.toString()
            is DateTime -> value.toString()
            is Decimal -> value.toString()
            is Enum -> value.toString()
            is Float -> value.toString()
            is Instant -> value.toString()
            is Int -> value.toString()
            is List -> value.joinToString(", ", "[", "]") { it.prettyPrint() }
            is Long -> value.toString()
            is Map -> value.entries.joinToString(", ", "{", "}") { (key, value) -> "$key: ${value.prettyPrint()}" }
            is Optional -> value?.prettyPrint() ?: "null"
            is String -> value
            is Struct -> value.joinToString(", ", "{", "}") { "${it.name}: \"${it.value.prettyPrint()}\"" }
            is Time -> value.toString()

            is UUID -> value.toString()
        }
    }

    fun matches(pattern: kotlin.String): kotlin.Boolean {
        return when (this) {
            is Boolean -> value == pattern.toBoolean()
            is Date -> value.toString().contains(pattern)
            is DateTime -> value.toString().contains(pattern)
            is Decimal -> value.toString().contains(pattern)
            is Double -> value.toString().contains(pattern)
            is Enum -> value.toString().contains(pattern, true)
            is Float -> value.toString().contains(pattern)
            is Instant -> value.toString().contains(pattern)
            is Int -> value.toString().contains(pattern)
            is List -> value.any { it.matches(pattern) }
            is Long -> value.toString().contains(pattern)
            is Optional -> value?.matches(pattern) ?: false
            is Map -> value.any { it.value.matches(pattern) }
            is String -> value.contains(pattern, true)
            is Struct -> value.any { it.value.matches(pattern) }
            is Time -> value.toString().contains(pattern)
            is UUID -> value.toString().contains(pattern)
        }
    }

    fun matches(other: Schema) = matches(other.value.toString())

    operator fun compareTo(other: Schema): kotlin.Int {
        return when (this) {
            is Boolean -> value.compareTo(other.value.toString().toBoolean())
            is Date -> value.toString().compareTo(other.value.toString())
            is DateTime -> value.toString().compareTo(other.value.toString())
            is Decimal -> value.compareTo(other.value.toString().toBigDecimal())
            is Double -> value.compareTo(other.value.toString().toDouble())
            is Enum -> value.toString().compareTo(other.value.toString())
            is Float -> value.compareTo(other.value.toString().toFloat())
            is Instant -> value.toString().compareTo(other.value.toString())
            is Int -> value.compareTo(other.value.toString().toInt())
            is Long -> value.compareTo(other.value.toString().toLong())
            is List -> 0 // TODO what's the best way to compare two lists?
            is Optional -> value?.compareTo(other) ?: 0
            is Map -> 0 // TODO what's the best way to compare two maps?
            is String -> value.compareTo(other.value.toString())
            is Struct -> 0 // TODO what's the best way to compare two structs?
            is Time -> value.toString().compareTo(other.value.toString())
            is UUID -> value.compareTo(java.util.UUID.fromString(other.value.toString()))
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
            is Boolean -> "Boolean"
            is Date -> "Date"
            is DateTime -> "DateTime"
            is Decimal -> "Decimal"
            is Double -> "Double"
            is Enum -> "Enum"
            is Float -> "Float"
            is Instant -> "Instant"
            is Int -> "Int"
            is List -> "List<${valueType.prettyPrint()}>"
            is Long -> "Long"
            is Optional -> "Optional<${value?.printTypes() ?: "Any"}>"
            is Map -> "Map<String,${valueType}>"
            is String -> "String"
            is Struct -> "Struct<${value.joinToString(", ") { "${it.name}: ${it.value.printTypes()}" }}>"
            is Time -> "Time"
            is UUID -> "UUID"
        }
    }
}
