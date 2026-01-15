package io.typestream.openai

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.Closeable
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

/**
 * Service for OpenAI API operations including chat completions and model listing.
 */
open class OpenAiService(
    private val apiKey: String = defaultApiKey()
) : Closeable {
    private val logger = KotlinLogging.logger {}
    private val httpClient = HttpClient.newHttpClient()
    private val json = Json { ignoreUnknownKeys = true }

    @Volatile
    private var cachedModels: List<OpenAiModel> = emptyList()

    data class OpenAiModel(
        val id: String,
        val name: String
    )

    // --- Model Listing ---

    @Serializable
    private data class ModelsResponse(
        val data: List<ModelData>
    )

    @Serializable
    private data class ModelData(
        val id: String,
        val owned_by: String
    )

    /**
     * Fetch available chat models from OpenAI API.
     * Filters to only chat-capable models (gpt-4*, gpt-3.5-turbo*).
     * Results are cached after the first fetch.
     */
    fun fetchModels(): List<OpenAiModel> {
        if (cachedModels.isNotEmpty()) return cachedModels

        if (apiKey.isBlank()) {
            logger.warn { "OpenAI API key not configured. Set OPENAI_API_KEY environment variable." }
            return emptyList()
        }

        return try {
            val request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.openai.com/v1/models"))
                .header("Authorization", "Bearer $apiKey")
                .GET()
                .build()

            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())

            if (response.statusCode() == 200) {
                val modelsResponse = json.decodeFromString(ModelsResponse.serializer(), response.body())
                cachedModels = modelsResponse.data
                    .filter { it.id.startsWith("gpt-4") || it.id.startsWith("gpt-3.5-turbo") }
                    .filter { !it.id.contains("instruct") } // Exclude instruct models, keep chat models
                    .map { OpenAiModel(id = it.id, name = it.id) }
                    .sortedBy { it.id }
                cachedModels
            } else {
                logger.warn { "OpenAI models fetch failed: HTTP ${response.statusCode()}" }
                emptyList()
            }
        } catch (e: Exception) {
            logger.debug { "Models fetch failed: ${e.message}" }
            emptyList()
        }
    }

    // --- Chat Completion ---

    @Serializable
    private data class ChatCompletionRequest(
        val model: String,
        val messages: List<ChatMessage>
    )

    @Serializable
    private data class ChatMessage(
        val role: String,
        val content: String
    )

    @Serializable
    private data class ChatCompletionResponse(
        val choices: List<ChatChoice>
    )

    @Serializable
    private data class ChatChoice(
        val message: ChatMessage
    )

    /**
     * Make a chat completion request to OpenAI.
     * Combines the user prompt with the message JSON.
     *
     * @param prompt The user's instruction prompt
     * @param messageJson The JSON string of the message to process
     * @param model The OpenAI model to use (default: gpt-4o-mini)
     * @return The AI's response text, or null if the request failed
     */
    open fun complete(prompt: String, messageJson: String, model: String = "gpt-4o-mini"): String? {
        if (apiKey.isBlank()) {
            logger.warn { "OpenAI API key not configured. Set OPENAI_API_KEY environment variable." }
            return null
        }

        return try {
            val fullPrompt = "$prompt\n\n$messageJson"
            val requestBody = json.encodeToString(
                ChatCompletionRequest.serializer(),
                ChatCompletionRequest(
                    model = model,
                    messages = listOf(ChatMessage(role = "user", content = fullPrompt))
                )
            )

            val request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.openai.com/v1/chat/completions"))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer $apiKey")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build()

            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())

            if (response.statusCode() == 200) {
                val completionResponse = json.decodeFromString(ChatCompletionResponse.serializer(), response.body())
                completionResponse.choices.firstOrNull()?.message?.content
            } else {
                logger.warn { "OpenAI chat completion failed: HTTP ${response.statusCode()} - ${response.body()}" }
                null
            }
        } catch (e: Exception) {
            logger.debug { "Chat completion failed: ${e.message}" }
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
