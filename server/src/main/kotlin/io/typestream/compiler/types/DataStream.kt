package io.typestream.compiler.types

import io.typestream.compiler.types.schema.Schema
import io.typestream.compiler.types.schema.empty
import kotlinx.serialization.Serializable

@Serializable
data class DataStream(
    val path: String,
    val schema: Schema,
    /** Original Avro schema string for pass-through scenarios (e.g., Debezium) */
    val originalAvroSchema: String? = null
) : Value {
    override val value = this
    val name = path.substringAfterLast("/")

    companion object {
        val devNull = listOf<DataStream>()
        fun fromString(path: String, value: String) = DataStream(path, Schema.String(value))
        fun fromLong(path: String, value: Long) = DataStream(path, Schema.Long(value))
    }

    fun merge(right: DataStream) = copy(
        path = if (path == right.path) path else "${path}_${right.path.substringAfterLast("/")}",
        schema = schema.merge(right.schema)
    )

    operator fun get(key: String) = schema.selectOne(key) ?: Schema.Struct.empty()

    fun hasField(key: String): Boolean {
        require(schema is Schema.Struct) { "schema is not a struct" }

        return schema.selectOne(key) != null
    }

    fun matches(pattern: String) = schema.matches(pattern)

    fun prettyPrint() = schema.prettyPrint()
    fun printTypes() = schema.printTypes()
    fun select(boundArgs: List<String>) = copy(schema = schema.select(boundArgs))

    /**
     * Add a new field to this DataStream's schema.
     * @param fieldName The name of the new field
     * @param value The schema value for the new field
     * @return A new DataStream with the added field
     */
    fun addField(fieldName: String, value: Schema): DataStream {
        require(schema is Schema.Struct) { "can only add field to struct schema" }
        val newField = Schema.Field(fieldName, value)
        val newFields = schema.value + newField
        return copy(schema = Schema.Struct(newFields))
    }

    /**
     * Get the value of a field as a string.
     * @param fieldName The name of the field to get
     * @return The string value of the field, or null if not found
     */
    fun selectFieldAsString(fieldName: String): String? {
        val fieldValue = schema.selectOne(fieldName) ?: return null
        return when (fieldValue) {
            is Schema.String -> fieldValue.value
            else -> fieldValue.value?.toString()
        }
    }
}
