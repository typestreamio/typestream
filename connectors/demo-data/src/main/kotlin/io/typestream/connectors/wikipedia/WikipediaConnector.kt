package io.typestream.connectors.wikipedia

import io.github.oshai.kotlinlogging.KotlinLogging
import io.typestream.connectors.Config
import io.typestream.connectors.avro.WikipediaChange
import io.typestream.connectors.kafka.MessageSender
import io.typestream.connectors.kafka.Producer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources
import java.time.Instant
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

private const val WIKIPEDIA_SSE_URL = "https://stream.wikimedia.org/v2/stream/recentchange"
private const val TOPIC = "wikipedia_changes"
private const val RETENTION_MS = 60 * 60 * 1000L // 1 hour

class WikipediaConnector(
    private val wikis: List<String>,
    private val sender: MessageSender = Producer(Config.fromEnv(TOPIC).copy(retentionMs = RETENTION_MS)),
) : AutoCloseable {
    private val logger = KotlinLogging.logger {}
    private val json = Json { ignoreUnknownKeys = true }

    private val client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.SECONDS)
        .build()

    private var eventSource: EventSource? = null
    private val running = AtomicBoolean(true)
    private val closeLatch = CountDownLatch(1)

    fun start() {
        logger.info { "Starting Wikipedia connector" + if (wikis.isNotEmpty()) " for wikis: $wikis" else " (all wikis)" }
        connect()
    }

    private fun connect() {
        val request = Request.Builder()
            .url(WIKIPEDIA_SSE_URL)
            .header("User-Agent", "TypeStream/1.0 (https://github.com/typestreamio/typestream)")
            .build()

        val factory = EventSources.createFactory(client)
        eventSource = factory.newEventSource(request, object : EventSourceListener() {
            override fun onOpen(eventSource: EventSource, response: Response) {
                logger.info { "SSE connected to Wikipedia stream" }
            }

            override fun onEvent(eventSource: EventSource, id: String?, type: String?, data: String) {
                handleEvent(data)
            }

            override fun onFailure(eventSource: EventSource, t: Throwable?, response: Response?) {
                logger.error(t) { "SSE failure: ${t?.message}, response=${response?.code} ${response?.message}" }
                if (running.get()) {
                    scheduleReconnect()
                }
            }

            override fun onClosed(eventSource: EventSource) {
                logger.info { "SSE connection closed" }
                if (running.get()) {
                    scheduleReconnect()
                } else {
                    closeLatch.countDown()
                }
            }
        })
    }

    private fun handleEvent(data: String) {
        try {
            val event = json.decodeFromString<WikipediaEvent>(data)

            // Filter by wiki if specified
            if (wikis.isNotEmpty() && event.wiki !in wikis) {
                return
            }

            val change = event.toAvro()
            sender.send(event.id?.toString() ?: "${event.wiki}-${System.currentTimeMillis()}", change)
            logger.debug { "Produced change for ${event.wiki}: ${event.title}" }
        } catch (e: Exception) {
            logger.error(e) { "Failed to process event: ${data.take(500)}" }
        }
    }

    private fun scheduleReconnect() {
        logger.info { "Scheduling reconnect in 5 seconds..." }
        Thread {
            Thread.sleep(5000)
            if (running.get()) {
                logger.info { "Attempting to reconnect..." }
                connect()
            }
        }.start()
    }

    fun awaitTermination() {
        closeLatch.await()
    }

    override fun close() {
        logger.info { "Shutting down Wikipedia connector..." }
        running.set(false)
        eventSource?.cancel()
        sender.close()
        client.dispatcher.executorService.shutdown()
        client.connectionPool.evictAll()
        closeLatch.countDown()
    }
}

@Serializable
private data class WikipediaEvent(
    val id: Long? = null,
    val wiki: String,
    val title: String,
    val user: String,
    val type: String,
    val namespace: Int = 0,
    val bot: Boolean = false,
    val minor: Boolean = false,
    val comment: String? = null,
    val length: LengthInfo? = null,
    val server_name: String = "",
    val timestamp: Long? = null,
) {
    fun toAvro(): WikipediaChange {
        val ts = timestamp?.let { Instant.ofEpochSecond(it) } ?: Instant.now()

        return WikipediaChange.newBuilder()
            .setId(id ?: 0L)
            .setWiki(wiki)
            .setTitle(title)
            .setUser(user)
            .setType(type)
            .setNamespace(namespace)
            .setBot(bot)
            .setMinor(minor)
            .setComment(comment)
            .setOldLength(length?.old ?: 0)
            .setNewLength(length?.new ?: 0)
            .setServerName(server_name)
            .setTimestamp(ts)
            .build()
    }
}

@Serializable
private data class LengthInfo(
    val old: Int? = null,
    val new: Int? = null,
)
