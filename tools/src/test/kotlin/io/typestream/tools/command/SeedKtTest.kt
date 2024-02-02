package io.typestream.tools.command

import io.typestream.config.testing.testConfig
import io.typestream.testing.TestKafka
import io.typestream.testing.kafka.AdminClientWrapper
import io.typestream.testing.kafka.KafkaConsumerWrapper
import io.typestream.testing.kafka.RecordsExpected
import io.typestream.testing.model.Author
import io.typestream.testing.model.Book
import io.typestream.testing.model.PageView
import io.typestream.testing.model.Rating
import io.typestream.testing.model.User
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers

@Testcontainers
internal class SeedKtTest {

    @Container
    private val testKafka = TestKafka()

    private lateinit var adminClientWrapper: AdminClientWrapper
    private lateinit var kafkaConsumerWrapper: KafkaConsumerWrapper

    @BeforeEach
    fun setup() {
        adminClientWrapper = AdminClientWrapper(testKafka.bootstrapServers)
        kafkaConsumerWrapper = KafkaConsumerWrapper(testKafka.bootstrapServers, testKafka.schemaRegistryAddress)
    }

    @ParameterizedTest
    @ValueSource(strings = ["avro", "proto"])
    fun seeds(encoding: String) {
        seed(testConfig(testKafka), listOf(encoding))

        assertThat(adminClientWrapper.listTopics()).contains("authors", "books", "users", "ratings", "page_views")

        val allRecords = kafkaConsumerWrapper.consume(encoding, listOf(
            "authors" to 3,
            "books" to 10,
            "users" to 2,
            "ratings" to 6,
            "page_views" to 1,
        ).map { (topic, expected) -> RecordsExpected(topic, expected) })

        val authors = allRecords.filterIsInstance<Author>()

        assertThat(authors).extracting("name").containsExactlyInAnyOrder(
            "Emily St. John Mandel",
            "Octavia E. Butler",
            "Chimamanda Ngozi Adichie"
        )

        val users = allRecords.filterIsInstance<User>()

        assertThat(users).extracting("name").containsExactlyInAnyOrder("Grace Hopper", "Margaret Hamilton")

        val books = allRecords.filterIsInstance<Book>()

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

        val ratings = allRecords.count { it is Rating }

        assertThat(ratings).isEqualTo(6)

        val pageViews = allRecords.count { it is PageView }

        assertThat(pageViews).isEqualTo(1)
    }
}
