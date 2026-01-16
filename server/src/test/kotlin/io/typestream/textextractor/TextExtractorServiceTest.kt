package io.typestream.textextractor

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.writeText

internal class TextExtractorServiceTest {

    @Nested
    inner class ExtractWithMissingFile {
        @Test
        fun `extract returns null when file does not exist`() {
            val service = TextExtractorService("http://localhost:9998")

            val result = service.extract("/nonexistent/path/file.pdf")

            assertThat(result).isNull()
        }
    }

    @Nested
    inner class ExtractWithInvalidInput {
        @Test
        fun `extract returns null for empty file path`() {
            val service = TextExtractorService("http://localhost:9998")

            val result = service.extract("")

            assertThat(result).isNull()
        }
    }

    @Nested
    inner class ExtractWithUnreachableService {
        @Test
        fun `extract returns null when Tika service is unreachable`(@TempDir tempDir: Path) {
            val testFile = tempDir.resolve("test.txt")
            testFile.writeText("Hello, World!")
            val service = TextExtractorService("http://localhost:59999") // Non-existent port

            val result = service.extract(testFile.toString())

            assertThat(result).isNull()
        }

        @Test
        fun `isAvailable returns false when Tika service is unreachable`() {
            val service = TextExtractorService("http://localhost:59999") // Non-existent port

            assertThat(service.isAvailable()).isFalse
        }
    }

    @Nested
    inner class ResourceManagement {
        @Test
        fun `close can be called without error`() {
            val service = TextExtractorService("http://localhost:9998")

            // Should not throw
            service.close()
        }

        @Test
        fun `service implements Closeable`() {
            val service = TextExtractorService("http://localhost:9998")

            assertThat(service).isInstanceOf(java.io.Closeable::class.java)
        }
    }

    @Nested
    inner class DefaultConfiguration {
        @Test
        fun `default URL uses TIKA_URL environment variable or fallback`() {
            // This tests the defaultTikaUrl function behavior
            val defaultUrl = TextExtractorService.defaultTikaUrl()

            // Either from env var or the fallback
            assertThat(defaultUrl).matches("http://.*:\\d+")
        }
    }
}
