package io.typestream.filesystem.kafka

import io.typestream.config.KafkaConfig
import io.typestream.config.SchemaRegistryConfig
import io.typestream.testing.TestKafkaContainer
import org.assertj.core.api.Assertions.assertThatCode
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.testcontainers.junit.jupiter.Testcontainers

@Testcontainers
internal class KafkaClusterDirectoryTest {

    companion object {
        private val testKafka = TestKafkaContainer.instance
    }

    @Test
    fun `verifyConnectivity succeeds when Kafka and Schema Registry are reachable`() {
        val kafkaConfig = KafkaConfig(
            bootstrapServers = testKafka.bootstrapServers,
            schemaRegistry = SchemaRegistryConfig(url = testKafka.schemaRegistryAddress),
        )
        val clusterDir = KafkaClusterDirectory("test", kafkaConfig)

        assertThatCode { clusterDir.verifyConnectivity() }.doesNotThrowAnyException()
    }

    @Test
    fun `verifyConnectivity fails when Kafka is unreachable`() {
        val kafkaConfig = KafkaConfig(
            bootstrapServers = "localhost:19092",
            schemaRegistry = SchemaRegistryConfig(url = testKafka.schemaRegistryAddress),
        )
        val clusterDir = KafkaClusterDirectory("bad-kafka", kafkaConfig)

        assertThatThrownBy { clusterDir.verifyConnectivity(timeoutMs = 3_000) }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("Kafka cluster 'bad-kafka' is unreachable")
            .hasMessageContaining("localhost:19092")
    }

    @Test
    fun `verifyConnectivity fails when Schema Registry is unreachable`() {
        val kafkaConfig = KafkaConfig(
            bootstrapServers = testKafka.bootstrapServers,
            schemaRegistry = SchemaRegistryConfig(url = "http://localhost:19093"),
        )
        val clusterDir = KafkaClusterDirectory("bad-sr", kafkaConfig)

        assertThatThrownBy { clusterDir.verifyConnectivity() }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("Schema Registry for cluster 'bad-sr' is unreachable")
            .hasMessageContaining("localhost:19093")
    }
}
