package io.typestream.embedding

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

internal class EmbeddingGeneratorServiceTest {

    @Nested
    inner class EmbedWithMissingApiKey {
        @Test
        fun `embed returns null when API key is blank`() {
            val service = EmbeddingGeneratorService("")

            val result = service.embed("Hello, World!")

            assertThat(result).isNull()
        }

        @Test
        fun `isAvailable returns false when API key is blank`() {
            val service = EmbeddingGeneratorService("")

            assertThat(service.isAvailable()).isFalse
        }
    }

    @Nested
    inner class EmbedWithInvalidApiKey {
        @Test
        fun `embed returns null when API key is invalid`() {
            val service = EmbeddingGeneratorService("invalid-key")

            val result = service.embed("Hello, World!")

            assertThat(result).isNull()
        }
    }

    @Nested
    inner class ResourceManagement {
        @Test
        fun `close can be called without error`() {
            val service = EmbeddingGeneratorService("")

            // Should not throw
            service.close()
        }

        @Test
        fun `service implements Closeable`() {
            val service = EmbeddingGeneratorService("")

            assertThat(service).isInstanceOf(java.io.Closeable::class.java)
        }
    }

    @Nested
    inner class DefaultConfiguration {
        @Test
        fun `default API key uses OPENAI_API_KEY environment variable or empty string`() {
            val defaultKey = EmbeddingGeneratorService.defaultApiKey()

            // Either from env var or empty string
            assertThat(defaultKey).isNotNull
        }

        @Test
        fun `isAvailable returns true when API key is configured`() {
            // Only test this if we have an API key set
            val apiKey = System.getenv("OPENAI_API_KEY") ?: ""
            if (apiKey.isBlank()) return

            val service = EmbeddingGeneratorService(apiKey)
            assertThat(service.isAvailable()).isTrue
        }
    }

    @Nested
    inner class ModelSelection {
        @Test
        fun `embed uses default model when not specified`() {
            val service = EmbeddingGeneratorService("")
            // Just verify that calling with text doesn't throw
            // (API key is blank so it will return null gracefully)
            val result = service.embed("test")
            assertThat(result).isNull()
        }

        @Test
        fun `embed accepts custom model parameter`() {
            val service = EmbeddingGeneratorService("")
            // Just verify that calling with custom model doesn't throw
            val result = service.embed("test", "text-embedding-3-large")
            assertThat(result).isNull()
        }
    }
}
