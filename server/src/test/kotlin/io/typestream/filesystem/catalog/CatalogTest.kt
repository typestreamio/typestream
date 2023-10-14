package io.typestream.filesystem.catalog

import io.typestream.compiler.types.schema.Schema
import io.typestream.config.SourcesConfig
import io.typestream.filesystem.FileSystem
import io.typestream.testing.RedpandaContainerWrapper
import io.typestream.testing.avro.buildUser
import io.typestream.testing.konfig.testKonfig
import kotlinx.coroutines.Dispatchers
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.tuple
import org.junit.jupiter.api.Test
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers

@Testcontainers
internal class CatalogTest {

    @Container
    private val testKafka = RedpandaContainerWrapper()

    @Test
    fun `fetches avro schema`() {
        testKafka.produceRecords("users", buildUser("Grace Hopper"))
        val catalog = Catalog(SourcesConfig(testKonfig(testKafka)), Dispatchers.IO)

        catalog.refresh()

        val users = catalog["${FileSystem.KAFKA_CLUSTERS_PREFIX}/local/topics/users"]
        requireNotNull(users)

        assertThat(users).extracting("dataStream.path")
            .isEqualTo("${FileSystem.KAFKA_CLUSTERS_PREFIX}/local/topics/users")

        val schema = users.dataStream.schema

        require(schema is Schema.Struct)

        assertThat(schema.value)
            .hasSize(2)
            .extracting("name", "value")
            .contains(
                tuple("id", Schema.String.empty),
                tuple("name", Schema.String.empty)
            )
    }
}
