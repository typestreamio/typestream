package io.typestream.connectors.webvisits

import io.github.oshai.kotlinlogging.KotlinLogging
import io.typestream.connectors.Config
import io.typestream.connectors.kafka.MessageSender
import io.typestream.connectors.kafka.Producer
import io.typestream.connectors.webvisits.geo.CidrRegistry
import io.typestream.connectors.webvisits.geo.CountryIpGenerator
import io.typestream.connectors.webvisits.geo.CountryWeight
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

private const val DEFAULT_TOPIC = "web_visits"
private const val RETENTION_MS = 60 * 60 * 1000L // 1 hour retention

class WebVisitsConnector(
    private val ratePerSecond: Double = 10.0,
    private val countries: List<String>? = null,
    topic: String = DEFAULT_TOPIC,
    private val sender: MessageSender = Producer(Config.fromEnv(topic).copy(retentionMs = RETENTION_MS))
) : AutoCloseable {
    private val logger = KotlinLogging.logger {}
    private val running = AtomicBoolean(true)
    private val closeLatch = CountDownLatch(1)
    private val messageCount = AtomicLong(0)

    private val generator: WebVisitGenerator
    private val simulator = TrafficSimulator(baseRatePerSecond = ratePerSecond)

    init {
        val ipGenerator = if (countries != null) {
            // Use equal weights for specified countries
            val weights = countries.map { CountryWeight(it.uppercase(), 1.0) }
            CountryIpGenerator(CidrRegistry(countries.map { it.uppercase() }), weights)
        } else {
            CountryIpGenerator()
        }
        generator = WebVisitGenerator(ipGenerator)
    }

    fun start() {
        logger.info { "Starting WebVisits connector" }
        logger.info { "  Rate: ~$ratePerSecond events/sec" }
        logger.info { "  Countries: ${countries?.joinToString(",") ?: "default weighted distribution"}" }

        Thread {
            try {
                while (running.get()) {
                    val visit = generator.generate()
                    sender.send(visit.sessionId, visit)

                    val count = messageCount.incrementAndGet()
                    if (count % 100 == 0L) {
                        logger.info { "Produced $count web visits (latest: ${visit.ipAddress} -> ${visit.urlPath})" }
                    } else {
                        logger.debug { "Produced visit: ${visit.ipAddress} -> ${visit.urlPath}" }
                    }

                    val delay = simulator.nextDelayMs()
                    Thread.sleep(delay)
                }
            } catch (e: InterruptedException) {
                logger.info { "Generator thread interrupted" }
            } finally {
                closeLatch.countDown()
            }
        }.apply {
            name = "webvisits-generator"
            isDaemon = true
            start()
        }
    }

    fun awaitTermination() {
        closeLatch.await()
    }

    override fun close() {
        logger.info { "Shutting down WebVisits connector (produced ${messageCount.get()} messages)..." }
        running.set(false)
        sender.close()
    }
}
