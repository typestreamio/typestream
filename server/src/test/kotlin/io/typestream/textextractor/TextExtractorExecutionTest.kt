package io.typestream.textextractor

import io.typestream.compiler.node.NodeTextExtractor
import io.typestream.compiler.types.DataStream
import io.typestream.compiler.types.schema.Schema
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.testcontainers.containers.GenericContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.utility.DockerImageName
import java.nio.file.Path
import kotlin.io.path.writeText

internal class TextExtractorExecutionTest {

    private val testSchema = Schema.Struct(
        listOf(
            Schema.Field("id", Schema.String("123")),
            Schema.Field("file_path", Schema.String("/path/to/document.pdf")),
            Schema.Field("name", Schema.String("test"))
        )
    )

    private val testDataStream = DataStream("/test/topic", testSchema)

    @Nested
    inner class ApplyToShell {

        @Test
        fun `adds text field to datastream`() {
            val textExtractorService = TestTextExtractorService(mapOf("/path/to/document.pdf" to "Extracted content"))
            val node = NodeTextExtractor("extractor-1", "file_path", "text")

            val result = TextExtractorExecution.applyToShell(node, listOf(testDataStream), textExtractorService)

            assertThat(result).hasSize(1)
            val outputSchema = result[0].schema as Schema.Struct
            val fieldNames = outputSchema.value.map { it.name }
            assertThat(fieldNames).contains("text")
        }

        @Test
        fun `sets text value from extraction`() {
            val textExtractorService = TestTextExtractorService(mapOf("/path/to/document.pdf" to "Hello, World!"))
            val node = NodeTextExtractor("extractor-1", "file_path", "text")

            val result = TextExtractorExecution.applyToShell(node, listOf(testDataStream), textExtractorService)

            val outputSchema = result[0].schema as Schema.Struct
            val textField = outputSchema.value.find { it.name == "text" }
            assertThat(textField?.value).isEqualTo(Schema.String("Hello, World!"))
        }

        @Test
        fun `sets empty string when extraction returns null`() {
            val textExtractorService = TestTextExtractorService(emptyMap())
            val node = NodeTextExtractor("extractor-1", "file_path", "text")

            val result = TextExtractorExecution.applyToShell(node, listOf(testDataStream), textExtractorService)

            val outputSchema = result[0].schema as Schema.Struct
            val textField = outputSchema.value.find { it.name == "text" }
            assertThat(textField?.value).isEqualTo(Schema.String(""))
        }

        @Test
        fun `handles empty file path field gracefully`() {
            val emptyPathSchema = Schema.Struct(
                listOf(
                    Schema.Field("id", Schema.String("123")),
                    Schema.Field("file_path", Schema.String("")),
                )
            )
            val emptyPathDataStream = DataStream("/test/topic", emptyPathSchema)
            val textExtractorService = TestTextExtractorService(emptyMap())
            val node = NodeTextExtractor("extractor-1", "file_path", "text")

            val result = TextExtractorExecution.applyToShell(node, listOf(emptyPathDataStream), textExtractorService)

            val outputSchema = result[0].schema as Schema.Struct
            val textField = outputSchema.value.find { it.name == "text" }
            assertThat(textField?.value).isEqualTo(Schema.String(""))
        }

        @Test
        fun `processes multiple datastreams`() {
            val textExtractorService = TestTextExtractorService(
                mapOf(
                    "/path/to/doc1.pdf" to "Content 1",
                    "/path/to/doc2.pdf" to "Content 2"
                )
            )
            val stream1 = DataStream(
                "/test/topic",
                Schema.Struct(listOf(Schema.Field("file_path", Schema.String("/path/to/doc1.pdf"))))
            )
            val stream2 = DataStream(
                "/test/topic",
                Schema.Struct(listOf(Schema.Field("file_path", Schema.String("/path/to/doc2.pdf"))))
            )
            val node = NodeTextExtractor("extractor-1", "file_path", "extracted_text")

            val result = TextExtractorExecution.applyToShell(node, listOf(stream1, stream2), textExtractorService)

            assertThat(result).hasSize(2)
            val text1 = (result[0].schema as Schema.Struct).value.find { it.name == "extracted_text" }?.value
            val text2 = (result[1].schema as Schema.Struct).value.find { it.name == "extracted_text" }?.value
            assertThat(text1).isEqualTo(Schema.String("Content 1"))
            assertThat(text2).isEqualTo(Schema.String("Content 2"))
        }

        @Test
        fun `uses custom output field name`() {
            val textExtractorService = TestTextExtractorService(mapOf("/path/to/document.pdf" to "Content"))
            val node = NodeTextExtractor("extractor-1", "file_path", "document_content")

            val result = TextExtractorExecution.applyToShell(node, listOf(testDataStream), textExtractorService)

            val outputSchema = result[0].schema as Schema.Struct
            val fieldNames = outputSchema.value.map { it.name }
            assertThat(fieldNames).contains("document_content")
            assertThat(fieldNames).doesNotContain("text")
        }

        @Test
        fun `preserves existing fields from input`() {
            val textExtractorService = TestTextExtractorService(mapOf("/path/to/document.pdf" to "Content"))
            val node = NodeTextExtractor("extractor-1", "file_path", "text")

            val result = TextExtractorExecution.applyToShell(node, listOf(testDataStream), textExtractorService)

            val outputSchema = result[0].schema as Schema.Struct
            val fieldNames = outputSchema.value.map { it.name }
            assertThat(fieldNames).containsAll(listOf("id", "file_path", "name", "text"))
        }
    }

    /**
     * Test implementation of TextExtractorService that returns predictable results.
     */
    private class TestTextExtractorService(private val extractionTable: Map<String, String>) : TextExtractorService("http://localhost:9998") {
        override fun extract(filePath: String): String? = extractionTable[filePath]
    }
}

/**
 * Integration tests that use a real Tika container to extract text from actual files.
 */
@Testcontainers
internal class TextExtractorIntegrationTest {

    companion object {
        @Container
        @JvmStatic
        val tikaContainer: GenericContainer<*> = GenericContainer(DockerImageName.parse("apache/tika:2.9.2.1"))
            .withExposedPorts(9998)
    }

    @Nested
    inner class ExtractFromRealFile {

        @Test
        fun `extracts text from a plain text file`(@TempDir tempDir: Path) {
            val testFile = tempDir.resolve("test.txt")
            testFile.writeText("Hello, this is a test document with some content.")

            val tikaUrl = "http://${tikaContainer.host}:${tikaContainer.getMappedPort(9998)}"
            val service = TextExtractorService(tikaUrl)

            val extractedText = service.extract(testFile.toString())

            assertThat(extractedText).isNotNull
            assertThat(extractedText).contains("Hello")
            assertThat(extractedText).contains("test document")
        }

        @Test
        fun `extracts text and adds to datastream`(@TempDir tempDir: Path) {
            val testFile = tempDir.resolve("document.txt")
            testFile.writeText("Important content from the document.")

            val tikaUrl = "http://${tikaContainer.host}:${tikaContainer.getMappedPort(9998)}"
            val service = TextExtractorService(tikaUrl)

            val schema = Schema.Struct(
                listOf(
                    Schema.Field("id", Schema.String("doc-1")),
                    Schema.Field("file_path", Schema.String(testFile.toString()))
                )
            )
            val dataStream = DataStream("/test/topic", schema)
            val node = NodeTextExtractor("extractor", "file_path", "extracted_text")

            val result = TextExtractorExecution.applyToShell(node, listOf(dataStream), service)

            assertThat(result).hasSize(1)
            val outputSchema = result[0].schema as Schema.Struct
            val extractedField = outputSchema.value.find { it.name == "extracted_text" }
            assertThat(extractedField).isNotNull
            val extractedValue = (extractedField?.value as Schema.String).value
            assertThat(extractedValue).contains("Important content")
        }

        @Test
        fun `handles multiple files in pipeline`(@TempDir tempDir: Path) {
            val file1 = tempDir.resolve("doc1.txt")
            val file2 = tempDir.resolve("doc2.txt")
            file1.writeText("First document content")
            file2.writeText("Second document content")

            val tikaUrl = "http://${tikaContainer.host}:${tikaContainer.getMappedPort(9998)}"
            val service = TextExtractorService(tikaUrl)

            val stream1 = DataStream(
                "/test/topic",
                Schema.Struct(listOf(Schema.Field("path", Schema.String(file1.toString()))))
            )
            val stream2 = DataStream(
                "/test/topic",
                Schema.Struct(listOf(Schema.Field("path", Schema.String(file2.toString()))))
            )
            val node = NodeTextExtractor("extractor", "path", "text")

            val result = TextExtractorExecution.applyToShell(node, listOf(stream1, stream2), service)

            assertThat(result).hasSize(2)
            val text1 = ((result[0].schema as Schema.Struct).value.find { it.name == "text" }?.value as Schema.String).value
            val text2 = ((result[1].schema as Schema.Struct).value.find { it.name == "text" }?.value as Schema.String).value
            assertThat(text1).contains("First document")
            assertThat(text2).contains("Second document")
        }
    }
}
