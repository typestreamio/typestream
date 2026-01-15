package io.typestream.compiler.types

import io.typestream.compiler.types.schema.Schema
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

internal class TypeRulesTest {

    @Nested
    inner class InferGeoIp {

        private val inputSchema = Schema.Struct(
            listOf(
                Schema.Field("id", Schema.String("123")),
                Schema.Field("ip_address", Schema.String("8.8.8.8")),
                Schema.Field("name", Schema.String("test"))
            )
        )

        private val inputDataStream = DataStream("/test/topic", inputSchema)

        @Test
        fun `adds country code field to output schema`() {
            val result = TypeRules.inferGeoIp(inputDataStream, "ip_address", "country_code")

            val outputSchema = result.schema as Schema.Struct
            val fieldNames = outputSchema.value.map { it.name }
            assertThat(fieldNames).containsExactly("id", "ip_address", "name", "country_code")
        }

        @Test
        fun `new field has String type with zero value`() {
            val result = TypeRules.inferGeoIp(inputDataStream, "ip_address", "country_code")

            val outputSchema = result.schema as Schema.Struct
            val countryField = outputSchema.value.find { it.name == "country_code" }
            assertThat(countryField?.value).isEqualTo(Schema.String.zeroValue)
        }

        @Test
        fun `preserves original fields`() {
            val result = TypeRules.inferGeoIp(inputDataStream, "ip_address", "country_code")

            val outputSchema = result.schema as Schema.Struct
            val idField = outputSchema.value.find { it.name == "id" }
            val ipField = outputSchema.value.find { it.name == "ip_address" }
            val nameField = outputSchema.value.find { it.name == "name" }

            assertThat(idField?.value).isEqualTo(Schema.String("123"))
            assertThat(ipField?.value).isEqualTo(Schema.String("8.8.8.8"))
            assertThat(nameField?.value).isEqualTo(Schema.String("test"))
        }

        @Test
        fun `preserves original path`() {
            val result = TypeRules.inferGeoIp(inputDataStream, "ip_address", "country_code")

            assertThat(result.path).isEqualTo("/test/topic")
        }

        @Test
        fun `uses custom output field name`() {
            val result = TypeRules.inferGeoIp(inputDataStream, "ip_address", "geo_country")

            val outputSchema = result.schema as Schema.Struct
            val fieldNames = outputSchema.value.map { it.name }
            assertThat(fieldNames).contains("geo_country")
            assertThat(fieldNames).doesNotContain("country_code")
        }

        @Test
        fun `throws error when IP field does not exist`() {
            assertThatThrownBy {
                TypeRules.inferGeoIp(inputDataStream, "nonexistent_field", "country_code")
            }
                .isInstanceOf(IllegalArgumentException::class.java)
                .hasMessageContaining("IP field 'nonexistent_field' not found in schema")
                .hasMessageContaining("Available fields:")
        }

        @Test
        fun `error message lists available fields`() {
            assertThatThrownBy {
                TypeRules.inferGeoIp(inputDataStream, "nonexistent_field", "country_code")
            }
                .hasMessageContaining("id")
                .hasMessageContaining("ip_address")
                .hasMessageContaining("name")
        }

        @Test
        fun `throws error for non-struct schema`() {
            val stringSchema = Schema.String("test")
            val nonStructDataStream = DataStream("/test/topic", stringSchema)

            assertThatThrownBy {
                TypeRules.inferGeoIp(nonStructDataStream, "ip_address", "country_code")
            }
                .isInstanceOf(IllegalArgumentException::class.java)
                .hasMessageContaining("GeoIp requires struct schema")
        }

        @Test
        fun `works with different field names for IP`() {
            val schemaWithDifferentIpField = Schema.Struct(
                listOf(
                    Schema.Field("user_ip", Schema.String("1.2.3.4")),
                )
            )
            val dataStream = DataStream("/test/topic", schemaWithDifferentIpField)

            val result = TypeRules.inferGeoIp(dataStream, "user_ip", "country")

            val outputSchema = result.schema as Schema.Struct
            val fieldNames = outputSchema.value.map { it.name }
            assertThat(fieldNames).containsExactly("user_ip", "country")
        }
    }

    @Nested
    inner class InferTextExtractor {

        private val inputSchema = Schema.Struct(
            listOf(
                Schema.Field("id", Schema.String("123")),
                Schema.Field("file_path", Schema.String("/path/to/file.pdf")),
                Schema.Field("name", Schema.String("test"))
            )
        )

        private val inputDataStream = DataStream("/test/topic", inputSchema)

        @Test
        fun `adds text field to output schema`() {
            val result = TypeRules.inferTextExtractor(inputDataStream, "file_path", "text")

            val outputSchema = result.schema as Schema.Struct
            val fieldNames = outputSchema.value.map { it.name }
            assertThat(fieldNames).containsExactly("id", "file_path", "name", "text")
        }

        @Test
        fun `new field has String type with zero value`() {
            val result = TypeRules.inferTextExtractor(inputDataStream, "file_path", "text")

            val outputSchema = result.schema as Schema.Struct
            val textField = outputSchema.value.find { it.name == "text" }
            assertThat(textField?.value).isEqualTo(Schema.String.zeroValue)
        }

        @Test
        fun `preserves original fields`() {
            val result = TypeRules.inferTextExtractor(inputDataStream, "file_path", "text")

            val outputSchema = result.schema as Schema.Struct
            val idField = outputSchema.value.find { it.name == "id" }
            val filePathField = outputSchema.value.find { it.name == "file_path" }
            val nameField = outputSchema.value.find { it.name == "name" }

            assertThat(idField?.value).isEqualTo(Schema.String("123"))
            assertThat(filePathField?.value).isEqualTo(Schema.String("/path/to/file.pdf"))
            assertThat(nameField?.value).isEqualTo(Schema.String("test"))
        }

        @Test
        fun `preserves original path`() {
            val result = TypeRules.inferTextExtractor(inputDataStream, "file_path", "text")

            assertThat(result.path).isEqualTo("/test/topic")
        }

        @Test
        fun `uses custom output field name`() {
            val result = TypeRules.inferTextExtractor(inputDataStream, "file_path", "extracted_content")

            val outputSchema = result.schema as Schema.Struct
            val fieldNames = outputSchema.value.map { it.name }
            assertThat(fieldNames).contains("extracted_content")
            assertThat(fieldNames).doesNotContain("text")
        }

        @Test
        fun `throws error when file path field does not exist`() {
            assertThatThrownBy {
                TypeRules.inferTextExtractor(inputDataStream, "nonexistent_field", "text")
            }
                .isInstanceOf(IllegalArgumentException::class.java)
                .hasMessageContaining("file path field 'nonexistent_field' not found in schema")
                .hasMessageContaining("Available fields:")
        }

        @Test
        fun `error message lists available fields`() {
            assertThatThrownBy {
                TypeRules.inferTextExtractor(inputDataStream, "nonexistent_field", "text")
            }
                .hasMessageContaining("id")
                .hasMessageContaining("file_path")
                .hasMessageContaining("name")
        }

        @Test
        fun `throws error for non-struct schema`() {
            val stringSchema = Schema.String("test")
            val nonStructDataStream = DataStream("/test/topic", stringSchema)

            assertThatThrownBy {
                TypeRules.inferTextExtractor(nonStructDataStream, "file_path", "text")
            }
                .isInstanceOf(IllegalArgumentException::class.java)
                .hasMessageContaining("TextExtractor requires struct schema")
        }

        @Test
        fun `works with different field names for file path`() {
            val schemaWithDifferentPathField = Schema.Struct(
                listOf(
                    Schema.Field("document_path", Schema.String("/docs/file.pdf")),
                )
            )
            val dataStream = DataStream("/test/topic", schemaWithDifferentPathField)

            val result = TypeRules.inferTextExtractor(dataStream, "document_path", "content")

            val outputSchema = result.schema as Schema.Struct
            val fieldNames = outputSchema.value.map { it.name }
            assertThat(fieldNames).containsExactly("document_path", "content")
        }
    }
}
