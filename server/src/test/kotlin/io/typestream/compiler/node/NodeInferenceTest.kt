package io.typestream.compiler.node

import io.typestream.compiler.ast.Predicate
import io.typestream.compiler.types.DataStream
import io.typestream.compiler.types.Encoding
import io.typestream.compiler.types.InferenceContext
import io.typestream.compiler.types.schema.Schema
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

internal class NodeInferenceTest {

    // Shared test fixtures
    private val sampleStructSchema = Schema.Struct(
        listOf(
            Schema.Field("id", Schema.String("123")),
            Schema.Field("name", Schema.String("test")),
            Schema.Field("ip_address", Schema.String("8.8.8.8")),
            Schema.Field("file_path", Schema.String("/path/to/file.pdf")),
            Schema.Field("text_content", Schema.String("Hello world"))
        )
    )

    private val sampleDataStream = DataStream("/test/topic", sampleStructSchema)

    private val mockContext = object : InferenceContext {
        override fun lookupDataStream(path: String) = DataStream(path, sampleStructSchema)
        override fun lookupEncoding(path: String) = Encoding.AVRO
    }

    // ==================== Pass-through Nodes ====================

    @Nested
    inner class FilterNodeTests {
        @Test
        fun `passes through input schema unchanged`() {
            val node = Node.Filter("filter-1", byKey = false, predicate = Predicate.matches(".*"))
            val result = node.inferOutputSchema(sampleDataStream, Encoding.AVRO, mockContext)

            assertThat(result.dataStream.schema).isEqualTo(sampleDataStream.schema)
            assertThat(result.dataStream.path).isEqualTo(sampleDataStream.path)
            assertThat(result.encoding).isEqualTo(Encoding.AVRO)
        }

        @Test
        fun `preserves JSON encoding`() {
            val node = Node.Filter("filter-1", byKey = false, predicate = Predicate.matches(".*"))
            val result = node.inferOutputSchema(sampleDataStream, Encoding.JSON, mockContext)

            assertThat(result.encoding).isEqualTo(Encoding.JSON)
        }

        @Test
        fun `throws error when input is missing`() {
            val node = Node.Filter("filter-1", byKey = false, predicate = Predicate.matches(".*"))

            assertThatThrownBy {
                node.inferOutputSchema(null, null, mockContext)
            }.hasMessageContaining("filter filter-1 missing input")
        }
    }

    @Nested
    inner class MapNodeTests {
        @Test
        fun `passes through input schema unchanged`() {
            val node = Node.Map("map-1") { kv -> kv }
            val result = node.inferOutputSchema(sampleDataStream, Encoding.AVRO, mockContext)

            assertThat(result.dataStream.schema).isEqualTo(sampleDataStream.schema)
            assertThat(result.dataStream.path).isEqualTo(sampleDataStream.path)
            assertThat(result.encoding).isEqualTo(Encoding.AVRO)
        }
    }

    @Nested
    inner class GroupNodeTests {
        @Test
        fun `passes through input schema unchanged`() {
            val node = Node.Group("group-1") { kv -> kv.value }
            val result = node.inferOutputSchema(sampleDataStream, Encoding.AVRO, mockContext)

            assertThat(result.dataStream.schema).isEqualTo(sampleDataStream.schema)
            assertThat(result.encoding).isEqualTo(Encoding.AVRO)
        }
    }

    @Nested
    inner class CountNodeTests {
        @Test
        fun `passes through input schema unchanged`() {
            val node = Node.Count("count-1")
            val result = node.inferOutputSchema(sampleDataStream, Encoding.AVRO, mockContext)

            assertThat(result.dataStream.schema).isEqualTo(sampleDataStream.schema)
            assertThat(result.encoding).isEqualTo(Encoding.AVRO)
        }
    }

    @Nested
    inner class WindowedCountNodeTests {
        @Test
        fun `passes through input schema unchanged`() {
            val node = Node.WindowedCount("windowed-count-1", windowSizeSeconds = 60)
            val result = node.inferOutputSchema(sampleDataStream, Encoding.AVRO, mockContext)

            assertThat(result.dataStream.schema).isEqualTo(sampleDataStream.schema)
            assertThat(result.dataStream.path).isEqualTo(sampleDataStream.path)
            assertThat(result.encoding).isEqualTo(Encoding.AVRO)
        }

        @Test
        fun `preserves JSON encoding`() {
            val node = Node.WindowedCount("windowed-count-1", windowSizeSeconds = 60)
            val result = node.inferOutputSchema(sampleDataStream, Encoding.JSON, mockContext)

            assertThat(result.encoding).isEqualTo(Encoding.JSON)
        }

        @Test
        fun `throws error when input is missing`() {
            val node = Node.WindowedCount("windowed-count-1", windowSizeSeconds = 60)

            assertThatThrownBy {
                node.inferOutputSchema(null, null, mockContext)
            }.hasMessageContaining("windowedCount windowed-count-1 missing input")
        }

        @Test
        fun `accepts different window sizes`() {
            val node = Node.WindowedCount("windowed-count-1", windowSizeSeconds = 300)
            val result = node.inferOutputSchema(sampleDataStream, Encoding.AVRO, mockContext)

            assertThat(result.dataStream.schema).isEqualTo(sampleDataStream.schema)
        }
    }

    @Nested
    inner class ReduceLatestNodeTests {
        @Test
        fun `passes through input schema unchanged`() {
            val node = Node.ReduceLatest("reduce-1")
            val result = node.inferOutputSchema(sampleDataStream, Encoding.AVRO, mockContext)

            assertThat(result.dataStream.schema).isEqualTo(sampleDataStream.schema)
            assertThat(result.encoding).isEqualTo(Encoding.AVRO)
        }
    }

    @Nested
    inner class EachNodeTests {
        @Test
        fun `passes through input schema unchanged`() {
            val node = Node.Each("each-1") { _ -> }
            val result = node.inferOutputSchema(sampleDataStream, Encoding.AVRO, mockContext)

            assertThat(result.dataStream.schema).isEqualTo(sampleDataStream.schema)
            assertThat(result.encoding).isEqualTo(Encoding.AVRO)
        }
    }

    @Nested
    inner class NoOpNodeTests {
        @Test
        fun `passes through input schema unchanged`() {
            val node = Node.NoOp("noop-1")
            val result = node.inferOutputSchema(sampleDataStream, Encoding.AVRO, mockContext)

            assertThat(result.dataStream.schema).isEqualTo(sampleDataStream.schema)
            assertThat(result.encoding).isEqualTo(Encoding.AVRO)
        }

        @Test
        fun `handles null input for root node`() {
            val node = Node.NoOp("root")
            val result = node.inferOutputSchema(null, null, mockContext)

            // NoOp as root returns a placeholder DataStream
            assertThat(result.dataStream.path).isEqualTo("")
            assertThat(result.encoding).isEqualTo(Encoding.AVRO)
        }
    }

    @Nested
    inner class InspectorNodeTests {
        @Test
        fun `passes through input schema unchanged`() {
            val node = Node.Inspector("inspector-1", "test-label")
            val result = node.inferOutputSchema(sampleDataStream, Encoding.AVRO, mockContext)

            assertThat(result.dataStream.schema).isEqualTo(sampleDataStream.schema)
            assertThat(result.encoding).isEqualTo(Encoding.AVRO)
        }
    }

    // ==================== Source Nodes ====================

    @Nested
    inner class StreamSourceNodeTests {
        @Test
        fun `returns pre-resolved dataStream and encoding`() {
            val sourceDataStream = DataStream("/dev/kafka/local/topics/test", sampleStructSchema)
            val node = Node.StreamSource("source-1", sourceDataStream, Encoding.AVRO)
            val result = node.inferOutputSchema(null, null, mockContext)

            assertThat(result.dataStream).isEqualTo(sourceDataStream)
            assertThat(result.encoding).isEqualTo(Encoding.AVRO)
        }

        @Test
        fun `preserves PROTOBUF encoding`() {
            val sourceDataStream = DataStream("/dev/kafka/local/topics/test", sampleStructSchema)
            val node = Node.StreamSource("source-1", sourceDataStream, Encoding.PROTOBUF)
            val result = node.inferOutputSchema(null, null, mockContext)

            assertThat(result.encoding).isEqualTo(Encoding.PROTOBUF)
        }
    }

    @Nested
    inner class ShellSourceNodeTests {
        @Test
        fun `returns first dataStream schema`() {
            val dataStreams = listOf(
                DataStream("/shell/first", sampleStructSchema),
                DataStream("/shell/second", Schema.Struct(listOf()))
            )
            val node = Node.ShellSource("shell-1", dataStreams)
            val result = node.inferOutputSchema(null, null, mockContext)

            assertThat(result.dataStream.path).isEqualTo("/shell/first")
            assertThat(result.dataStream.schema).isEqualTo(sampleStructSchema)
        }

        @Test
        fun `returns JSON encoding`() {
            val dataStreams = listOf(DataStream("/shell/first", sampleStructSchema))
            val node = Node.ShellSource("shell-1", dataStreams)
            val result = node.inferOutputSchema(null, null, mockContext)

            assertThat(result.encoding).isEqualTo(Encoding.JSON)
        }

        @Test
        fun `throws error when no data streams`() {
            val node = Node.ShellSource("shell-1", emptyList())

            assertThatThrownBy {
                node.inferOutputSchema(null, null, mockContext)
            }.hasMessageContaining("ShellSource shell-1 has no data streams")
        }
    }

    // ==================== Sink Node ====================

    @Nested
    inner class SinkNodeTests {
        @Test
        fun `copies input schema with target path`() {
            val targetDataStream = DataStream("/dev/kafka/local/topics/output", Schema.String.zeroValue)
            val node = Node.Sink("sink-1", targetDataStream, Encoding.AVRO)
            val result = node.inferOutputSchema(sampleDataStream, Encoding.AVRO, mockContext)

            assertThat(result.dataStream.schema).isEqualTo(sampleDataStream.schema)
            assertThat(result.dataStream.path).isEqualTo("/dev/kafka/local/topics/output")
        }

        @Test
        fun `preserves input encoding`() {
            val targetDataStream = DataStream("/dev/kafka/local/topics/output", Schema.String.zeroValue)
            val node = Node.Sink("sink-1", targetDataStream, Encoding.JSON)
            val result = node.inferOutputSchema(sampleDataStream, Encoding.JSON, mockContext)

            assertThat(result.encoding).isEqualTo(Encoding.JSON)
        }

        @Test
        fun `throws error when input is missing`() {
            val targetDataStream = DataStream("/dev/kafka/local/topics/output", Schema.String.zeroValue)
            val node = Node.Sink("sink-1", targetDataStream, Encoding.AVRO)

            assertThatThrownBy {
                node.inferOutputSchema(null, null, mockContext)
            }.hasMessageContaining("sink sink-1 missing input")
        }
    }

    // ==================== Join Node ====================

    @Nested
    inner class JoinNodeTests {
        private val rightSchema = Schema.Struct(
            listOf(
                Schema.Field("right_id", Schema.String("456")),
                Schema.Field("right_name", Schema.String("right"))
            )
        )
        private val rightDataStream = DataStream("/test/right", rightSchema)

        @Test
        fun `merges left and right schemas`() {
            val node = Node.Join("join-1", rightDataStream, JoinType(byKey = true, isLookup = false))
            val result = node.inferOutputSchema(sampleDataStream, Encoding.AVRO, mockContext)

            val outputSchema = result.dataStream.schema as Schema.Struct
            val fieldNames = outputSchema.value.map { it.name }

            // Should contain fields from both left and right
            assertThat(fieldNames).contains("id", "name", "ip_address")
            assertThat(fieldNames).contains("right_id", "right_name")
        }

        @Test
        fun `clears originalAvroSchema after join`() {
            val inputWithAvro = sampleDataStream.copy(originalAvroSchema = """{"type": "null"}""")
            val node = Node.Join("join-1", rightDataStream, JoinType(byKey = true, isLookup = false))
            val result = node.inferOutputSchema(inputWithAvro, Encoding.AVRO, mockContext)

            assertThat(result.dataStream.originalAvroSchema).isNull()
        }

        @Test
        fun `preserves encoding through join`() {
            val node = Node.Join("join-1", rightDataStream, JoinType(byKey = true, isLookup = false))
            val result = node.inferOutputSchema(sampleDataStream, Encoding.PROTOBUF, mockContext)

            assertThat(result.encoding).isEqualTo(Encoding.PROTOBUF)
        }
    }

    // ==================== Enrichment Nodes ====================

    @Nested
    inner class GeoIpNodeTests {

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
            val node = Node.GeoIp("geoip-1", ipField = "ip_address", outputField = "country_code")
            val result = node.inferOutputSchema(inputDataStream, Encoding.AVRO, mockContext)

            val outputSchema = result.dataStream.schema as Schema.Struct
            val fieldNames = outputSchema.value.map { it.name }
            assertThat(fieldNames).containsExactly("id", "ip_address", "name", "country_code")
        }

        @Test
        fun `new field has String type with zero value`() {
            val node = Node.GeoIp("geoip-1", ipField = "ip_address", outputField = "country_code")
            val result = node.inferOutputSchema(inputDataStream, Encoding.AVRO, mockContext)

            val outputSchema = result.dataStream.schema as Schema.Struct
            val countryField = outputSchema.value.find { it.name == "country_code" }
            assertThat(countryField?.value).isEqualTo(Schema.String.zeroValue)
        }

        @Test
        fun `preserves original fields`() {
            val node = Node.GeoIp("geoip-1", ipField = "ip_address", outputField = "country_code")
            val result = node.inferOutputSchema(inputDataStream, Encoding.AVRO, mockContext)

            val outputSchema = result.dataStream.schema as Schema.Struct
            val idField = outputSchema.value.find { it.name == "id" }
            val ipField = outputSchema.value.find { it.name == "ip_address" }
            val nameField = outputSchema.value.find { it.name == "name" }

            assertThat(idField?.value).isEqualTo(Schema.String("123"))
            assertThat(ipField?.value).isEqualTo(Schema.String("8.8.8.8"))
            assertThat(nameField?.value).isEqualTo(Schema.String("test"))
        }

        @Test
        fun `preserves original path`() {
            val node = Node.GeoIp("geoip-1", ipField = "ip_address", outputField = "country_code")
            val result = node.inferOutputSchema(inputDataStream, Encoding.AVRO, mockContext)

            assertThat(result.dataStream.path).isEqualTo("/test/topic")
        }

        @Test
        fun `uses custom output field name`() {
            val node = Node.GeoIp("geoip-1", ipField = "ip_address", outputField = "geo_country")
            val result = node.inferOutputSchema(inputDataStream, Encoding.AVRO, mockContext)

            val outputSchema = result.dataStream.schema as Schema.Struct
            val fieldNames = outputSchema.value.map { it.name }
            assertThat(fieldNames).contains("geo_country")
            assertThat(fieldNames).doesNotContain("country_code")
        }

        @Test
        fun `throws error when IP field does not exist`() {
            val node = Node.GeoIp("geoip-1", ipField = "nonexistent_field", outputField = "country_code")

            assertThatThrownBy {
                node.inferOutputSchema(inputDataStream, Encoding.AVRO, mockContext)
            }
                .isInstanceOf(IllegalArgumentException::class.java)
                .hasMessageContaining("IP field 'nonexistent_field' not found in schema")
                .hasMessageContaining("Available fields:")
        }

        @Test
        fun `error message lists available fields`() {
            val node = Node.GeoIp("geoip-1", ipField = "nonexistent_field", outputField = "country_code")

            assertThatThrownBy {
                node.inferOutputSchema(inputDataStream, Encoding.AVRO, mockContext)
            }
                .hasMessageContaining("id")
                .hasMessageContaining("ip_address")
                .hasMessageContaining("name")
        }

        @Test
        fun `throws error for non-struct schema`() {
            val stringSchema = Schema.String("test")
            val nonStructDataStream = DataStream("/test/topic", stringSchema)
            val node = Node.GeoIp("geoip-1", ipField = "ip_address", outputField = "country_code")

            assertThatThrownBy {
                node.inferOutputSchema(nonStructDataStream, Encoding.AVRO, mockContext)
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
            val node = Node.GeoIp("geoip-1", ipField = "user_ip", outputField = "country")

            val result = node.inferOutputSchema(dataStream, Encoding.AVRO, mockContext)

            val outputSchema = result.dataStream.schema as Schema.Struct
            val fieldNames = outputSchema.value.map { it.name }
            assertThat(fieldNames).containsExactly("user_ip", "country")
        }
    }

    @Nested
    inner class TextExtractorNodeTests {

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
            val node = Node.TextExtractor("text-1", filePathField = "file_path", outputField = "text")
            val result = node.inferOutputSchema(inputDataStream, Encoding.AVRO, mockContext)

            val outputSchema = result.dataStream.schema as Schema.Struct
            val fieldNames = outputSchema.value.map { it.name }
            assertThat(fieldNames).containsExactly("id", "file_path", "name", "text")
        }

        @Test
        fun `new field has String type with zero value`() {
            val node = Node.TextExtractor("text-1", filePathField = "file_path", outputField = "text")
            val result = node.inferOutputSchema(inputDataStream, Encoding.AVRO, mockContext)

            val outputSchema = result.dataStream.schema as Schema.Struct
            val textField = outputSchema.value.find { it.name == "text" }
            assertThat(textField?.value).isEqualTo(Schema.String.zeroValue)
        }

        @Test
        fun `preserves original fields`() {
            val node = Node.TextExtractor("text-1", filePathField = "file_path", outputField = "text")
            val result = node.inferOutputSchema(inputDataStream, Encoding.AVRO, mockContext)

            val outputSchema = result.dataStream.schema as Schema.Struct
            val idField = outputSchema.value.find { it.name == "id" }
            val filePathField = outputSchema.value.find { it.name == "file_path" }
            val nameField = outputSchema.value.find { it.name == "name" }

            assertThat(idField?.value).isEqualTo(Schema.String("123"))
            assertThat(filePathField?.value).isEqualTo(Schema.String("/path/to/file.pdf"))
            assertThat(nameField?.value).isEqualTo(Schema.String("test"))
        }

        @Test
        fun `preserves original path`() {
            val node = Node.TextExtractor("text-1", filePathField = "file_path", outputField = "text")
            val result = node.inferOutputSchema(inputDataStream, Encoding.AVRO, mockContext)

            assertThat(result.dataStream.path).isEqualTo("/test/topic")
        }

        @Test
        fun `uses custom output field name`() {
            val node = Node.TextExtractor("text-1", filePathField = "file_path", outputField = "extracted_content")
            val result = node.inferOutputSchema(inputDataStream, Encoding.AVRO, mockContext)

            val outputSchema = result.dataStream.schema as Schema.Struct
            val fieldNames = outputSchema.value.map { it.name }
            assertThat(fieldNames).contains("extracted_content")
            assertThat(fieldNames).doesNotContain("text")
        }

        @Test
        fun `throws error when file path field does not exist`() {
            val node = Node.TextExtractor("text-1", filePathField = "nonexistent_field", outputField = "text")

            assertThatThrownBy {
                node.inferOutputSchema(inputDataStream, Encoding.AVRO, mockContext)
            }
                .isInstanceOf(IllegalArgumentException::class.java)
                .hasMessageContaining("file path field 'nonexistent_field' not found in schema")
                .hasMessageContaining("Available fields:")
        }

        @Test
        fun `error message lists available fields`() {
            val node = Node.TextExtractor("text-1", filePathField = "nonexistent_field", outputField = "text")

            assertThatThrownBy {
                node.inferOutputSchema(inputDataStream, Encoding.AVRO, mockContext)
            }
                .hasMessageContaining("id")
                .hasMessageContaining("file_path")
                .hasMessageContaining("name")
        }

        @Test
        fun `throws error for non-struct schema`() {
            val stringSchema = Schema.String("test")
            val nonStructDataStream = DataStream("/test/topic", stringSchema)
            val node = Node.TextExtractor("text-1", filePathField = "file_path", outputField = "text")

            assertThatThrownBy {
                node.inferOutputSchema(nonStructDataStream, Encoding.AVRO, mockContext)
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
            val node = Node.TextExtractor("text-1", filePathField = "document_path", outputField = "content")

            val result = node.inferOutputSchema(dataStream, Encoding.AVRO, mockContext)

            val outputSchema = result.dataStream.schema as Schema.Struct
            val fieldNames = outputSchema.value.map { it.name }
            assertThat(fieldNames).containsExactly("document_path", "content")
        }
    }

    @Nested
    inner class EmbeddingGeneratorNodeTests {

        private val inputSchema = Schema.Struct(
            listOf(
                Schema.Field("id", Schema.String("123")),
                Schema.Field("text_content", Schema.String("Hello world")),
                Schema.Field("name", Schema.String("test"))
            )
        )

        private val inputDataStream = DataStream("/test/topic", inputSchema)

        @Test
        fun `adds embedding field to output schema`() {
            val node = Node.EmbeddingGenerator("embed-1", textField = "text_content", outputField = "embedding", model = "text-embedding-ada-002")
            val result = node.inferOutputSchema(inputDataStream, Encoding.AVRO, mockContext)

            val outputSchema = result.dataStream.schema as Schema.Struct
            val fieldNames = outputSchema.value.map { it.name }
            assertThat(fieldNames).containsExactly("id", "text_content", "name", "embedding")
        }

        @Test
        fun `new field has List of Float type`() {
            val node = Node.EmbeddingGenerator("embed-1", textField = "text_content", outputField = "embedding", model = "text-embedding-ada-002")
            val result = node.inferOutputSchema(inputDataStream, Encoding.AVRO, mockContext)

            val outputSchema = result.dataStream.schema as Schema.Struct
            val embeddingField = outputSchema.value.find { it.name == "embedding" }
            assertThat(embeddingField?.value).isInstanceOf(Schema.List::class.java)
            val listSchema = embeddingField?.value as Schema.List
            assertThat(listSchema.valueType).isInstanceOf(Schema.Float::class.java)
        }

        @Test
        fun `preserves original fields`() {
            val node = Node.EmbeddingGenerator("embed-1", textField = "text_content", outputField = "embedding", model = "text-embedding-ada-002")
            val result = node.inferOutputSchema(inputDataStream, Encoding.AVRO, mockContext)

            val outputSchema = result.dataStream.schema as Schema.Struct
            val idField = outputSchema.value.find { it.name == "id" }
            val textField = outputSchema.value.find { it.name == "text_content" }
            val nameField = outputSchema.value.find { it.name == "name" }

            assertThat(idField?.value).isEqualTo(Schema.String("123"))
            assertThat(textField?.value).isEqualTo(Schema.String("Hello world"))
            assertThat(nameField?.value).isEqualTo(Schema.String("test"))
        }

        @Test
        fun `preserves original path`() {
            val node = Node.EmbeddingGenerator("embed-1", textField = "text_content", outputField = "embedding", model = "text-embedding-ada-002")
            val result = node.inferOutputSchema(inputDataStream, Encoding.AVRO, mockContext)

            assertThat(result.dataStream.path).isEqualTo("/test/topic")
        }

        @Test
        fun `uses custom output field name`() {
            val node = Node.EmbeddingGenerator("embed-1", textField = "text_content", outputField = "vector", model = "text-embedding-ada-002")
            val result = node.inferOutputSchema(inputDataStream, Encoding.AVRO, mockContext)

            val outputSchema = result.dataStream.schema as Schema.Struct
            val fieldNames = outputSchema.value.map { it.name }
            assertThat(fieldNames).contains("vector")
            assertThat(fieldNames).doesNotContain("embedding")
        }

        @Test
        fun `throws error when text field does not exist`() {
            val node = Node.EmbeddingGenerator("embed-1", textField = "nonexistent_field", outputField = "embedding", model = "text-embedding-ada-002")

            assertThatThrownBy {
                node.inferOutputSchema(inputDataStream, Encoding.AVRO, mockContext)
            }
                .isInstanceOf(IllegalArgumentException::class.java)
                .hasMessageContaining("text field 'nonexistent_field' not found in schema")
                .hasMessageContaining("Available fields:")
        }

        @Test
        fun `error message lists available fields`() {
            val node = Node.EmbeddingGenerator("embed-1", textField = "nonexistent_field", outputField = "embedding", model = "text-embedding-ada-002")

            assertThatThrownBy {
                node.inferOutputSchema(inputDataStream, Encoding.AVRO, mockContext)
            }
                .hasMessageContaining("id")
                .hasMessageContaining("text_content")
                .hasMessageContaining("name")
        }

        @Test
        fun `throws error for non-struct schema`() {
            val stringSchema = Schema.String("test")
            val nonStructDataStream = DataStream("/test/topic", stringSchema)
            val node = Node.EmbeddingGenerator("embed-1", textField = "text_content", outputField = "embedding", model = "text-embedding-ada-002")

            assertThatThrownBy {
                node.inferOutputSchema(nonStructDataStream, Encoding.AVRO, mockContext)
            }
                .isInstanceOf(IllegalArgumentException::class.java)
                .hasMessageContaining("EmbeddingGenerator requires struct schema")
        }

        @Test
        fun `works with different field names for text`() {
            val schemaWithDifferentTextField = Schema.Struct(
                listOf(
                    Schema.Field("description", Schema.String("Some description")),
                )
            )
            val dataStream = DataStream("/test/topic", schemaWithDifferentTextField)
            val node = Node.EmbeddingGenerator("embed-1", textField = "description", outputField = "desc_embedding", model = "text-embedding-ada-002")

            val result = node.inferOutputSchema(dataStream, Encoding.AVRO, mockContext)

            val outputSchema = result.dataStream.schema as Schema.Struct
            val fieldNames = outputSchema.value.map { it.name }
            assertThat(fieldNames).containsExactly("description", "desc_embedding")
        }
    }

    @Nested
    inner class OpenAiTransformerNodeTests {

        private val inputSchema = Schema.Struct(
            listOf(
                Schema.Field("id", Schema.String("123")),
                Schema.Field("message", Schema.String("Hello world")),
                Schema.Field("name", Schema.String("test"))
            )
        )

        private val inputDataStream = DataStream("/test/topic", inputSchema)

        @Test
        fun `adds AI response field to output schema`() {
            val node = Node.OpenAiTransformer("ai-1", prompt = "Summarize this", outputField = "ai_response", model = "gpt-4")
            val result = node.inferOutputSchema(inputDataStream, Encoding.AVRO, mockContext)

            val outputSchema = result.dataStream.schema as Schema.Struct
            val fieldNames = outputSchema.value.map { it.name }
            assertThat(fieldNames).containsExactly("id", "message", "name", "ai_response")
        }

        @Test
        fun `new field has String type with zero value`() {
            val node = Node.OpenAiTransformer("ai-1", prompt = "Summarize this", outputField = "ai_response", model = "gpt-4")
            val result = node.inferOutputSchema(inputDataStream, Encoding.AVRO, mockContext)

            val outputSchema = result.dataStream.schema as Schema.Struct
            val aiField = outputSchema.value.find { it.name == "ai_response" }
            assertThat(aiField?.value).isEqualTo(Schema.String.zeroValue)
        }

        @Test
        fun `preserves original fields`() {
            val node = Node.OpenAiTransformer("ai-1", prompt = "Summarize this", outputField = "ai_response", model = "gpt-4")
            val result = node.inferOutputSchema(inputDataStream, Encoding.AVRO, mockContext)

            val outputSchema = result.dataStream.schema as Schema.Struct
            val idField = outputSchema.value.find { it.name == "id" }
            val messageField = outputSchema.value.find { it.name == "message" }
            val nameField = outputSchema.value.find { it.name == "name" }

            assertThat(idField?.value).isEqualTo(Schema.String("123"))
            assertThat(messageField?.value).isEqualTo(Schema.String("Hello world"))
            assertThat(nameField?.value).isEqualTo(Schema.String("test"))
        }

        @Test
        fun `uses custom output field name`() {
            val node = Node.OpenAiTransformer("ai-1", prompt = "Summarize this", outputField = "summary", model = "gpt-4")
            val result = node.inferOutputSchema(inputDataStream, Encoding.AVRO, mockContext)

            val outputSchema = result.dataStream.schema as Schema.Struct
            val fieldNames = outputSchema.value.map { it.name }
            assertThat(fieldNames).contains("summary")
            assertThat(fieldNames).doesNotContain("ai_response")
        }

        @Test
        fun `throws error for non-struct schema`() {
            val stringSchema = Schema.String("test")
            val nonStructDataStream = DataStream("/test/topic", stringSchema)
            val node = Node.OpenAiTransformer("ai-1", prompt = "Summarize this", outputField = "ai_response", model = "gpt-4")

            assertThatThrownBy {
                node.inferOutputSchema(nonStructDataStream, Encoding.AVRO, mockContext)
            }
                .isInstanceOf(IllegalArgumentException::class.java)
                .hasMessageContaining("OpenAiTransformer requires struct schema")
        }

        @Test
        fun `preserves encoding`() {
            val node = Node.OpenAiTransformer("ai-1", prompt = "Summarize this", outputField = "ai_response", model = "gpt-4")
            val result = node.inferOutputSchema(inputDataStream, Encoding.JSON, mockContext)

            assertThat(result.encoding).isEqualTo(Encoding.JSON)
        }
    }
}
