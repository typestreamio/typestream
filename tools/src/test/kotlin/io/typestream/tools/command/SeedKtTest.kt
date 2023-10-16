package io.typestream.tools.command

import io.typestream.testing.RedpandaContainerWrapper
import io.typestream.testing.avro.Author
import io.typestream.testing.avro.Book
import io.typestream.testing.avro.User
import io.typestream.testing.kafka.AdminClientWrapper
import io.typestream.testing.kafka.KafkaConsumerWrapper
import io.typestream.testing.kafka.RecordsExpected
import io.typestream.testing.konfig.testKonfig
import io.typestream.tools.Config
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers

@Testcontainers
internal class SeedKtTest {

    @Container
    private val testKafka = RedpandaContainerWrapper()

    private lateinit var adminClientWrapper: AdminClientWrapper
    private lateinit var kafkaConsumerWrapper: KafkaConsumerWrapper

    @BeforeEach
    fun setup() {
        adminClientWrapper = AdminClientWrapper(testKafka.bootstrapServers)
        kafkaConsumerWrapper = KafkaConsumerWrapper(testKafka.bootstrapServers, testKafka.schemaRegistryAddress)
    }

    @Test
    fun `seeds correctly`() {
        seed(Config(testKonfig(testKafka)).kafkaClustersConfig)

        assertThat(adminClientWrapper.listTopics()).contains("authors", "books", "users", "ratings", "page_views")

        val allRecords = kafkaConsumerWrapper.consume(listOf(
            "authors" to 3,
            "books" to 10,
            "users" to 2,
            "ratings" to 6,
            "page_views" to 1,
        ).map { (topic, expected) -> RecordsExpected(topic, expected) })

        val authors = allRecords.filter { it.topic() == "authors" }.map { it.value() as Author }

        assertThat(authors).extracting("name").containsExactlyInAnyOrder(
            "Emily St. John Mandel",
            "Octavia E. Butler",
            "Chimamanda Ngozi Adichie"
        )

        val users = allRecords.filter { it.topic() == "users" }.map { it.value() as User }

        assertThat(users).extracting("name").containsExactlyInAnyOrder("Grace Hopper", "Margaret Hamilton")

        val books = allRecords.filter { it.topic() == "books" }.map { it.value() as Book }

        assertThat(books).extracting("title").containsExactlyInAnyOrder(
            "Station Eleven",
            "Sea of Tranquility",
            "The Glass Hotel",
            "Kindred",
            "Bloodchild",
            "Parable of the Sower",
            "Parable of the Talents",
            "Americanah",
            "Half of a Yellow Sun",
            "Purple Hibiscus",
        )

        val ratings = allRecords.count { it.topic() == "ratings" }

        assertThat(ratings).isEqualTo(6)

        val pageViews = allRecords.count { it.topic() == "page_views" }

        assertThat(pageViews).isEqualTo(1)
    }
}
