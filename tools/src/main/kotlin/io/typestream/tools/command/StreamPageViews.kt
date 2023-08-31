package io.typestream.tools.command

import io.typestream.testing.avro.buildBook
import io.typestream.testing.avro.toProducerRecords
import io.typestream.testing.kafka.KafkaConsumerWrapper
import io.typestream.testing.kafka.KafkaProducerWrapper
import io.typestream.tools.KafkaClustersConfig
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import java.time.Instant
import java.util.UUID

fun randomIpv4() = (0..3).map { (0..255).random() }.joinToString(".")

fun buildPageView(bookId: UUID): io.typestream.testing.avro.PageView =
    io.typestream.testing.avro.PageView.newBuilder().setViewedAt(Instant.now()).setIpAddress(randomIpv4())
        .setBookId(bookId).build()

fun streamPageViews(kafkaClustersConfig: KafkaClustersConfig) = runBlocking {
    val kafkaConfig = kafkaClustersConfig.clusters["local"]
    requireNotNull(kafkaConfig) {
        "local kafka cluster not found"
    }

    val kafkaProducer = KafkaProducerWrapper(kafkaConfig.bootstrapServers, kafkaConfig.schemaRegistryUrl)

    val kafkaConsumerWrapper = KafkaConsumerWrapper(kafkaConfig.bootstrapServers, kafkaConfig.schemaRegistryUrl)

    val authors = kafkaConsumerWrapper.consume<io.typestream.testing.avro.Author>("authors", 3)

    val emily = authors.find { it.name == "Emily St. John Mandel" } ?: error("Emily not found")
    val olivia = authors.find { it.name == "Octavia E. Butler" } ?: error("Octavia not found")

    val books = listOf(
        buildBook("Station Eleven", 300, emily.id),
        buildBook("The Sea of Tranquility", 400, emily.id),
        buildBook("The Glass Hotel", 500, emily.id),
        buildBook("Kindred", 200, olivia.id),
        buildBook("Bloodchild", 100, olivia.id)
    )

    while (true) {
        val book = books.random()
        val view = buildPageView(book.id)
        println("$view")
        kafkaProducer.produce(toProducerRecords("page_views", view) { v -> v.get("book_id").toString() })
        delay(System.getenv("STREAM_PAGE_VIEWS_DELAY_MS")?.toLong() ?: 500)
    }
}
