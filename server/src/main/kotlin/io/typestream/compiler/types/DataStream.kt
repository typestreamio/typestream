package io.typestream.compiler.types

import io.typestream.compiler.types.schema.Schema
import io.typestream.compiler.types.schema.empty
import kotlinx.serialization.Serializable

@Serializable
data class DataStream(val path: String, val schema: Schema) : Value {
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
}
