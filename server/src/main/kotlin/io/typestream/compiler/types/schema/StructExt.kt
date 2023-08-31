package io.typestream.compiler.types.schema

import kotlinx.serialization.json.Json.Default.parseToJsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject


fun Schema.Struct.Companion.empty() = Schema.Struct(emptyList())

//TODO everything is a string right now
fun Schema.Struct.Companion.fromJSON(json: String): Schema.Struct {
    val jsonObject = parseToJsonElement(json).jsonObject
    val values = jsonObject.map { (name, value) ->
        Schema.Named(
            name, Schema.String(
                when (value) {
                    is JsonPrimitive -> value.content
                    else -> value.toString()
                }
            )
        )
    }

    return Schema.Struct(values)
}

fun Schema.Struct.flatten(): Schema.Struct {
    val values = value.flatMap { namedValue ->
        when (namedValue.value) {
            is Schema.Struct ->
                namedValue.value.flatten().value.map { nested ->
                    Schema.Named("${namedValue.name}.${nested.name}", nested.value)
                }

            else -> listOf(namedValue)
        }
    }
    return Schema.Struct(values)
}

fun Schema.Struct.nest(): Schema.Struct {
    val values = mutableMapOf<String, Schema.Named>()

    value.forEach {
        val parts = it.name.split(".")
        recursiveBuild(values, parts, it.value)
    }

    return Schema.Struct(values.values.toList())
}

private fun recursiveBuild(values: MutableMap<String, Schema.Named>, parts: List<String>, schema: Schema) {
    val name = parts.first()

    if (parts.size == 1) {
        values[name] = Schema.Named(name, schema)
    } else {
        val nested = values[name] ?: Schema.Named(name, Schema.Struct(emptyList()))
        val nestedStruct = nested.value as Schema.Struct
        val nestedValues = mutableMapOf<String, Schema.Named>()
        nestedStruct.value.forEach {
            nestedValues[it.name] = it
        }
        recursiveBuild(nestedValues, parts.drop(1), schema)
        values[name] = Schema.Named(name, Schema.Struct(nestedValues.values.toList()))
    }
}

