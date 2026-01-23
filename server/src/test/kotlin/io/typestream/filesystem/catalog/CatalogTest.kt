package io.typestream.filesystem.catalog

import io.typestream.compiler.types.schema.Schema
import io.typestream.config.testing.testConfig
import io.typestream.filesystem.FileSystem
import io.typestream.testing.TestKafka
import io.typestream.testing.TestKafkaContainer
import io.typestream.testing.model.Rating
import kotlinx.coroutines.Dispatchers
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.tuple
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.testcontainers.junit.jupiter.Testcontainers
import java.util.UUID

@Testcontainers
internal class CatalogTest {
    companion object {
        private val testKafka = TestKafkaContainer.instance
    }

    //TODO add proto as soon as we support official imports
    @ParameterizedTest
    @ValueSource(strings = ["avro"])
    fun `fetches schema`(encoding: String) {
        val topic = TestKafka.uniqueTopic("ratings")
        testKafka.produceRecords(
            topic,
            encoding,
            Rating(
                id = UUID.randomUUID().toString(),
                bookId = UUID.randomUUID().toString(),
                userId = UUID.randomUUID().toString(),
                rating = 5
            )
        )
        val catalog = Catalog(testConfig(testKafka).sources, Dispatchers.IO)

        catalog.refresh()

        val ratingsPath = "${FileSystem.KAFKA_CLUSTERS_PREFIX}/local/topics/$topic"
        val ratings = catalog[ratingsPath]
        requireNotNull(ratings)
        assertThat(ratings).extracting("dataStream.path").isEqualTo(ratingsPath)

        val schema = ratings.dataStream.schema

        require(schema is Schema.Struct)

        assertThat(schema.value)
            .hasSize(4)
            .extracting("name", "value")
            .contains(
                tuple("user_id", Schema.UUID.zeroValue),
                tuple("book_id", Schema.UUID.zeroValue),
                tuple("rating", Schema.Int(0)),
                tuple("rated_at", Schema.Instant.zeroValue(Schema.Instant.Precision.MILLIS))
            )
    }
}
