package io.typestream.server

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

internal class JsonUtilsTest {

    @Nested
    inner class BuildJsonString {

        @Test
        fun `handles special characters properly`() {
            val map = mapOf(
                "backslash" to "C:\\path\\to\\file",
                "newline" to "line1\nline2",
                "quote" to "He said \"hello\"",
                "tab" to "a\tb",
                "carriage_return" to "before\rafter"
            )

            val json = buildJsonString(map)

            // Verify proper escaping - backslashes and special chars should be escaped
            assertThat(json).contains("\\\\") // escaped backslash
            assertThat(json).contains("\\n") // escaped newline
            assertThat(json).contains("\\\"") // escaped quote
            assertThat(json).contains("\\t") // escaped tab
            assertThat(json).contains("\\r") // escaped carriage return
        }

        @Test
        fun `handles nested maps`() {
            val map = mapOf(
                "outer" to mapOf(
                    "inner" to "value",
                    "nested" to mapOf("deep" to "nested_value")
                )
            )

            val json = buildJsonString(map)

            assertThat(json).contains("\"outer\"")
            assertThat(json).contains("\"inner\"")
            assertThat(json).contains("\"value\"")
            assertThat(json).contains("\"deep\"")
            assertThat(json).contains("\"nested_value\"")
        }

        @Test
        fun `handles various number types`() {
            val map = mapOf(
                "int" to 42,
                "long" to 123L,
                "double" to 3.14,
                "float" to 2.5f
            )

            val json = buildJsonString(map)

            assertThat(json).contains("\"int\":42")
            assertThat(json).contains("\"long\":123")
            assertThat(json).contains("\"double\":3.14")
            assertThat(json).contains("\"float\":2.5")
        }

        @Test
        fun `handles boolean values`() {
            val map = mapOf(
                "enabled" to true,
                "disabled" to false
            )

            val json = buildJsonString(map)

            assertThat(json).contains("\"enabled\":true")
            assertThat(json).contains("\"disabled\":false")
        }

        @Test
        fun `handles null values`() {
            val map = mapOf<String, Any?>(
                "present" to "value",
                "absent" to null
            )

            val json = buildJsonString(map)

            assertThat(json).contains("\"present\":\"value\"")
            assertThat(json).contains("\"absent\":null")
        }

        @Test
        fun `handles empty map`() {
            val map = emptyMap<String, Any?>()

            val json = buildJsonString(map)

            assertThat(json).isEqualTo("{}")
        }

        @Test
        fun `handles unicode characters`() {
            val map = mapOf(
                "emoji" to "Hello 👋",
                "chinese" to "你好",
                "arabic" to "مرحبا"
            )

            val json = buildJsonString(map)

            assertThat(json).contains("Hello 👋")
            assertThat(json).contains("你好")
            assertThat(json).contains("مرحبا")
        }

        @Test
        fun `handles mixed types in map`() {
            val map = mapOf(
                "string" to "text",
                "number" to 42,
                "boolean" to true,
                "nested" to mapOf("key" to "value")
            )

            val json = buildJsonString(map)

            assertThat(json).contains("\"string\":\"text\"")
            assertThat(json).contains("\"number\":42")
            assertThat(json).contains("\"boolean\":true")
            assertThat(json).contains("\"nested\":{\"key\":\"value\"}")
        }
    }

    @Nested
    inner class AnyToJsonElement {

        @Test
        fun `converts non-string map keys to strings`() {
            val map = mapOf(
                "outer" to mapOf(
                    1 to "one",
                    2 to "two"
                )
            )

            val json = buildJsonString(map)

            // Integer keys should be converted to string keys
            assertThat(json).contains("\"1\":\"one\"")
            assertThat(json).contains("\"2\":\"two\"")
        }

        @Test
        fun `handles null map keys by converting to string null`() {
            val mapWithNullKey = mutableMapOf<Any?, Any?>()
            mapWithNullKey[null] = "value_for_null_key"
            mapWithNullKey["normal"] = "normal_value"

            val outerMap = mapOf("data" to mapWithNullKey)
            val json = buildJsonString(outerMap)

            assertThat(json).contains("\"null\":\"value_for_null_key\"")
            assertThat(json).contains("\"normal\":\"normal_value\"")
        }

        @Test
        fun `converts unknown types to string using toString`() {
            data class CustomType(val x: Int, val y: Int)

            val map = mapOf(
                "custom" to CustomType(1, 2)
            )

            val json = buildJsonString(map)

            // Should contain the toString() representation
            assertThat(json).contains("CustomType(x=1, y=2)")
        }
    }
}
