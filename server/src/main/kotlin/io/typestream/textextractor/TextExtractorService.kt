package io.typestream.textextractor

import io.github.oshai.kotlinlogging.KotlinLogging
import java.io.Closeable
import java.io.File
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

/**
 * Service for extracting text from files using Apache Tika.
 * Connects to a Tika server running in a container.
 */
open class TextExtractorService(
    private val tikaUrl: String = defaultTikaUrl()
) : Closeable {
    private val logger = KotlinLogging.logger {}
    private val httpClient = HttpClient.newHttpClient()

    /**
     * Extract text from a local file.
     * @param filePath The path to the local file
     * @return Extracted text content or null if extraction failed
     */
    open fun extract(filePath: String): String? {
        return try {
            val file = File(filePath)
            if (!file.exists()) {
                logger.warn { "File not found: $filePath" }
                return null
            }

            val request = HttpRequest.newBuilder()
                .uri(URI.create("$tikaUrl/tika"))
                .header("Accept", "text/plain")
                .PUT(HttpRequest.BodyPublishers.ofFile(file.toPath()))
                .build()

            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())

            if (response.statusCode() == 200) {
                response.body()
            } else {
                logger.warn { "Tika extraction failed for $filePath: HTTP ${response.statusCode()}" }
                null
            }
        } catch (e: Exception) {
            logger.debug { "Text extraction failed for $filePath: ${e.message}" }
            null
        }
    }

    /**
     * Check if the Tika service is available.
     */
    fun isAvailable(): Boolean {
        return try {
            val request = HttpRequest.newBuilder()
                .uri(URI.create("$tikaUrl/tika"))
                .GET()
                .build()
            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
            response.statusCode() == 200
        } catch (e: Exception) {
            false
        }
    }

    override fun close() {
        // HttpClient doesn't need explicit cleanup in Java 11+
    }

    companion object {
        fun defaultTikaUrl(): String {
            return System.getenv("TIKA_URL") ?: "http://tika:9998"
        }
    }
}
