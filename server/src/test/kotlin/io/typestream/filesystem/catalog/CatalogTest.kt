package io.typestream.filesystem.catalog

import io.typestream.testing.avro.buildUser
import io.typestream.testing.konfig.testKonfig
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import io.typestream.compiler.types.schema.Schema
import io.typestream.config.SourcesConfig
import io.typestream.filesystem.FileSystem
import io.typestream.testing.RedpandaContainerWrapper
import io.typestream.testing.avro.buildUser
import io.typestream.testing.konfig.testKonfig
import io.typestream.testing.until
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.tuple
import org.junit.jupiter.api.Test
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers

@ExperimentalCoroutinesApi
@Testcontainers
internal class CatalogTest {

    @Container
    private val testKafka = RedpandaContainerWrapper()

    @Test
    fun `fetches avro schema`() = runTest {
        testKafka.produceRecords("users", buildUser("Grace Hopper"))
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        val catalog = Catalog(SourcesConfig(testKonfig(testKafka)), testDispatcher)

        val job = launch(testDispatcher) { catalog.watch() }

        until {
            requireNotNull(catalog["${FileSystem.KAFKA_CLUSTERS_PREFIX}/local/topics/users"])
        }

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

        job.cancel()
    }
}
