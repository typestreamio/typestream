package io.typestream.tools.command

import io.typestream.testing.RedpandaContainerWrapper
import io.typestream.testing.kafka.AdminClientWrapper
import io.typestream.testing.kafka.KafkaConsumerWrapper
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

        assertThat(adminClientWrapper.listTopics()).contains(
            "authors", "books", "users", "ratings", "page_views"
        )

        val authors = kafkaConsumerWrapper.consume<io.typestream.testing.avro.Author>("authors", 3)

        assertThat(authors).extracting("name").containsExactlyInAnyOrder(
            "Emily St. John Mandel",
            "Octavia E. Butler",
            "Chimamanda Ngozi Adichie"
        )

        val users = kafkaConsumerWrapper.consume<io.typestream.testing.avro.User>("users", 2)

        assertThat(users).extracting("name").containsExactlyInAnyOrder("Grace Hopper", "Margaret Hamilton")

        val books = kafkaConsumerWrapper.consume<io.typestream.testing.avro.Book>("books", 10)

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

        val ratings = kafkaConsumerWrapper.consume<io.typestream.testing.avro.Rating>("ratings", 6)

        assertThat(ratings).hasSize(6)

        val pageViews = kafkaConsumerWrapper.consume<io.typestream.testing.avro.PageView>("page_views", 1)

        assertThat(pageViews).hasSize(1)
    }
}
