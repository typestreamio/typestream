package io.typestream.server

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

/**
 * Converts a map to a JSON string using kotlinx.serialization for proper RFC 7159 compliance.
 * Handles all special characters including \, \n, \r, \t, and control characters.
 */
internal fun buildJsonString(map: Map<String, Any?>): String {
    return Json.encodeToString(JsonObject.serializer(), mapToJsonObject(map))
}

/**
 * Recursively converts a Map<String, Any?> to a JsonObject.
 */
internal fun mapToJsonObject(map: Map<String, Any?>): JsonObject {
    return buildJsonObject {
        for ((key, value) in map) {
            put(key, anyToJsonElement(value))
        }
    }
}

/**
 * Converts any value to a JsonElement.
 * Handles null values, primitives, and nested maps with proper type safety.
 */
internal fun anyToJsonElement(value: Any?): JsonElement {
    return when (value) {
        null -> JsonNull
        is String -> JsonPrimitive(value)
        is Number -> JsonPrimitive(value)
        is Boolean -> JsonPrimitive(value)
        is Map<*, *> -> {
            // Safely convert map with any key types to string-keyed map
            val stringMap = value.entries.associate { (k, v) ->
                (k?.toString() ?: "null") to v
            }
            mapToJsonObject(stringMap)
        }
        else -> JsonPrimitive(value.toString())
    }
}
