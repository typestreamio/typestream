package io.typestream.connectors.coinbase

import io.github.oshai.kotlinlogging.KotlinLogging
import io.typestream.connectors.Config
import io.typestream.connectors.avro.CryptoTicker
import io.typestream.connectors.kafka.MessageSender
import io.typestream.connectors.kafka.Producer
import kotlinx.coroutines.delay
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.time.Instant
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

private const val COINBASE_WS_URL = "wss://ws-feed.exchange.coinbase.com"
private const val TOPIC = "crypto_tickers"

class CoinbaseConnector(
    private val products: List<String>,
    private val sender: MessageSender = Producer(Config.fromEnv(TOPIC)),
) : AutoCloseable {
    private val logger = KotlinLogging.logger {}
    private val json = Json { ignoreUnknownKeys = true }

    private val client = OkHttpClient.Builder()
        .pingInterval(30, TimeUnit.SECONDS)
        .build()

    private var webSocket: WebSocket? = null
    private val running = AtomicBoolean(true)
    private val closeLatch = CountDownLatch(1)

    fun start() {
        logger.info { "Starting Coinbase connector for products: $products" }
        connect()
    }

    private fun connect() {
        val request = Request.Builder()
            .url(COINBASE_WS_URL)
            .build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                logger.info { "WebSocket connected to Coinbase" }
                subscribe(webSocket)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                handleMessage(text)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                logger.error(t) { "WebSocket failure: ${response?.message}" }
                if (running.get()) {
                    scheduleReconnect()
                }
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                logger.info { "WebSocket closing: $code - $reason" }
                webSocket.close(1000, null)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                logger.info { "WebSocket closed: $code - $reason" }
                if (running.get()) {
                    scheduleReconnect()
                } else {
                    closeLatch.countDown()
                }
            }
        })
    }

    private fun subscribe(webSocket: WebSocket) {
        val subscribeMessage = buildSubscribeMessage(products)
        logger.info { "Subscribing to ticker channel: $subscribeMessage" }
        webSocket.send(subscribeMessage)
    }

    private fun buildSubscribeMessage(products: List<String>): String {
        val productList = products.joinToString(",") { "\"$it\"" }
        return """
            {
                "type": "subscribe",
                "channels": [
                    {
                        "name": "ticker",
                        "product_ids": [$productList]
                    }
                ]
            }
        """.trimIndent()
    }

    private fun handleMessage(text: String) {
        try {
            val message = json.decodeFromString<CoinbaseMessage>(text)

            when (message.type) {
                "ticker" -> {
                    val ticker = message.toAvro()
                    sender.send(message.product_id ?: "unknown", ticker)
                    logger.debug { "Produced ticker for ${message.product_id}: ${message.price}" }
                }
                "subscriptions" -> {
                    logger.info { "Subscription confirmed: $text" }
                }
                "error" -> {
                    logger.error { "Coinbase error: ${message.message}" }
                }
                else -> {
                    logger.debug { "Ignoring message type: ${message.type}" }
                }
            }
        } catch (e: Exception) {
            logger.error(e) { "Failed to process message: $text" }
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
        logger.info { "Shutting down Coinbase connector..." }
        running.set(false)
        webSocket?.close(1000, "Shutdown")
        sender.close()
        client.dispatcher.executorService.shutdown()
        client.connectionPool.evictAll()
    }
}

@Serializable
private data class CoinbaseMessage(
    val type: String,
    val product_id: String? = null,
    val price: String? = null,
    val open_24h: String? = null,
    val volume_24h: String? = null,
    val low_24h: String? = null,
    val high_24h: String? = null,
    val best_bid: String? = null,
    val best_ask: String? = null,
    val side: String? = null,
    val last_size: String? = null,
    val trade_id: Long? = null,
    val sequence: Long? = null,
    val time: String? = null,
    val message: String? = null,
) {
    fun toAvro(): CryptoTicker {
        val timestamp = time?.let {
            try {
                Instant.parse(it)
            } catch (_: Exception) {
                Instant.now()
            }
        } ?: Instant.now()

        return CryptoTicker.newBuilder()
            .setProductId(product_id ?: "unknown")
            .setPrice(price ?: "0")
            .setOpen24h(open_24h ?: "")
            .setVolume24h(volume_24h ?: "")
            .setLow24h(low_24h ?: "")
            .setHigh24h(high_24h ?: "")
            .setBestBid(best_bid ?: "")
            .setBestAsk(best_ask ?: "")
            .setSide(side ?: "")
            .setLastSize(last_size ?: "")
            .setTradeId(trade_id ?: 0L)
            .setSequence(sequence ?: 0L)
            .setTimestamp(timestamp)
            .build()
    }
}
