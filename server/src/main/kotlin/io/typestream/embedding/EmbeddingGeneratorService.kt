package io.typestream.embedding

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.Closeable
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

/**
 * Service for generating text embeddings using OpenAI's embedding API.
 */
open class EmbeddingGeneratorService(
    private val apiKey: String = defaultApiKey()
) : Closeable {
    private val logger = KotlinLogging.logger {}
    private val httpClient = HttpClient.newHttpClient()
    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    private data class EmbeddingRequest(
        val input: String,
        val model: String
    )

    @Serializable
    private data class EmbeddingResponse(
        val data: List<EmbeddingData>
    )

    @Serializable
    private data class EmbeddingData(
        val embedding: List<Float>,
        val index: Int
    )

    /**
     * Generate embeddings for the given text.
     * @param text The text to embed
     * @param model The embedding model to use (default: text-embedding-3-small)
     * @return List of floats representing the embedding vector, or null if generation failed
     */
    open fun embed(text: String, model: String = "text-embedding-3-small"): List<Float>? {
        if (apiKey.isBlank()) {
            logger.warn { "OpenAI API key not configured. Set OPENAI_API_KEY environment variable." }
            return null
        }

        return try {
            val requestBody = json.encodeToString(EmbeddingRequest.serializer(), EmbeddingRequest(text, model))

            val request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.openai.com/v1/embeddings"))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer $apiKey")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build()

            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())

            if (response.statusCode() == 200) {
                val embeddingResponse = json.decodeFromString(EmbeddingResponse.serializer(), response.body())
                embeddingResponse.data.firstOrNull()?.embedding
            } else {
                logger.warn { "OpenAI embedding failed: HTTP ${response.statusCode()} - ${response.body()}" }
                null
            }
        } catch (e: Exception) {
            logger.debug { "Embedding generation failed: ${e.message}" }
            null
        }
    }

    /**
     * Check if the OpenAI API is available and configured.
     */
    fun isAvailable(): Boolean {
        return apiKey.isNotBlank()
    }

    override fun close() {
        // HttpClient doesn't need explicit cleanup in Java 11+
    }

    companion object {
        fun defaultApiKey(): String {
            return System.getenv("OPENAI_API_KEY") ?: ""
        }
    }
}
