package io.typestream.server

import io.typestream.grpc.connection_service.Connection
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

internal class ConnectionServiceTest {

    private lateinit var connectionService: ConnectionService

    @BeforeEach
    fun setUp() {
        connectionService = ConnectionService()
    }

    @AfterEach
    fun tearDown() {
        connectionService.close()
    }

    private fun qdrantConfig(
        grpcUrl: String = "http://localhost:6334",
        connectorGrpcUrl: String = "",
        apiKey: String = ""
    ): Connection.QdrantConnectionConfig = Connection.QdrantConnectionConfig.newBuilder()
        .setId("test-qdrant")
        .setName("test-qdrant")
        .setRestUrl("http://localhost:6333")
        .setGrpcUrl(grpcUrl)
        .setConnectorGrpcUrl(connectorGrpcUrl)
        .setApiKey(apiKey)
        .build()

    @Suppress("UNCHECKED_CAST")
    private fun connectorConfigOf(result: Map<String, Any>): Map<String, Any> =
        result["config"] as Map<String, Any>

    @Test
    fun `builds qdrant sink connector config`() {
        val result = connectionService.buildQdrantSinkConnectorConfig(
            name = "test-connector",
            config = qdrantConfig(),
            topics = "qdrant-sink-sink-1-123",
            collectionName = "help_articles"
        )

        assertThat(result["name"]).isEqualTo("test-connector")
        val config = connectorConfigOf(result)
        assertThat(config["connector.class"]).isEqualTo("io.qdrant.kafka.QdrantSinkConnector")
        assertThat(config["tasks.max"]).isEqualTo("1")
        assertThat(config["topics"]).isEqualTo("qdrant-sink-sink-1-123")
        assertThat(config["key.converter"]).isEqualTo("org.apache.kafka.connect.storage.StringConverter")
        assertThat(config["value.converter"]).isEqualTo("org.apache.kafka.connect.json.JsonConverter")
        assertThat(config["value.converter.schemas.enable"]).isEqualTo("false")
        assertThat(config["errors.tolerance"]).isEqualTo("all")
        assertThat(config["errors.deadletterqueue.topic.name"]).isEqualTo("test-connector-dlq")
    }

    @Test
    fun `uses connector grpc url when set and falls back to grpc url when empty`() {
        val withConnectorUrl = connectorConfigOf(
            connectionService.buildQdrantSinkConnectorConfig(
                name = "c1",
                config = qdrantConfig(connectorGrpcUrl = "http://qdrant:6334"),
                topics = "t",
                collectionName = "c"
            )
        )
        assertThat(withConnectorUrl["qdrant.grpc.url"]).isEqualTo("http://qdrant:6334")

        val withFallback = connectorConfigOf(
            connectionService.buildQdrantSinkConnectorConfig(
                name = "c2",
                config = qdrantConfig(grpcUrl = "http://localhost:6334", connectorGrpcUrl = ""),
                topics = "t",
                collectionName = "c"
            )
        )
        assertThat(withFallback["qdrant.grpc.url"]).isEqualTo("http://localhost:6334")
    }

    @Test
    fun `includes api key only when set`() {
        val withoutKey = connectorConfigOf(
            connectionService.buildQdrantSinkConnectorConfig(
                name = "c1",
                config = qdrantConfig(),
                topics = "t",
                collectionName = "c"
            )
        )
        assertThat(withoutKey).doesNotContainKey("qdrant.api.key")

        val withKey = connectorConfigOf(
            connectionService.buildQdrantSinkConnectorConfig(
                name = "c2",
                config = qdrantConfig(apiKey = "secret"),
                topics = "t",
                collectionName = "c"
            )
        )
        assertThat(withKey["qdrant.api.key"]).isEqualTo("secret")
    }

    @Test
    fun `rejects blank collection name`() {
        assertThatThrownBy {
            connectionService.buildQdrantSinkConnectorConfig(
                name = "c1",
                config = qdrantConfig(),
                topics = "t",
                collectionName = ""
            )
        }.hasMessageContaining("Collection name")
    }
}
