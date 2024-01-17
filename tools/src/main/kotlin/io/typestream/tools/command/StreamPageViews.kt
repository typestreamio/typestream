package io.typestream.tools.command

import io.typestream.config.Config
import io.typestream.testing.kafka.KafkaConsumerWrapper
import io.typestream.testing.kafka.KafkaProducerWrapper
import io.typestream.testing.kafka.RecordsExpected
import io.typestream.testing.model.Author
import io.typestream.testing.model.Book
import io.typestream.testing.model.PageView
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking

fun streamPageViews(config: Config, args: List<String>) = runBlocking {
    val encoding = args.firstOrNull() ?: "avro"
    val kafkaConfig = config.sources.kafka["local"]
    requireNotNull(kafkaConfig) { "local kafka cluster not found" }

    val kafkaProducer = KafkaProducerWrapper(kafkaConfig.bootstrapServers, kafkaConfig.schemaRegistry.url)

    val kafkaConsumerWrapper = KafkaConsumerWrapper(kafkaConfig.bootstrapServers, kafkaConfig.schemaRegistry.url)

    val authors = kafkaConsumerWrapper.consume(encoding, listOf(("authors" to 3)).map { (topic, expected) ->
        RecordsExpected(topic, expected)
    }).filterIsInstance<Author>()

    val emily = authors.find { it.name == "Emily St. John Mandel" } ?: error("Emily not found")
    val octavia = authors.find { it.name == "Octavia E. Butler" } ?: error("Octavia not found")

    val books = listOf(
        Book(title = "Station Eleven", wordCount = 300, authorId = emily.id),
        Book(title = "The Sea of Tranquility", wordCount = 400, authorId = emily.id),
        Book(title = "The Glass Hotel", wordCount = 500, authorId = emily.id),
        Book(title = "Kindred", wordCount = 200, authorId = octavia.id),
        Book(title = "Bloodchild", wordCount = 100, authorId = octavia.id)
    )

    while (true) {
        val book = books.random()
        val view = PageView(book.id, book.id)
        println("$view")
        kafkaProducer.produce("page_views", encoding, listOf(view))
        delay(System.getenv("STREAM_PAGE_VIEWS_DELAY_MS")?.toLong() ?: 500)
    }
}
