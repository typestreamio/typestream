package io.typestream.connectors

data class Config(
    val bootstrapServers: String,
    val schemaRegistryUrl: String,
    val topic: String,
    val retentionMs: Long = 15 * 60 * 1000, // 15 minutes default for high-volume
) {
    companion object {
        fun fromEnv(topic: String): Config {
            val bootstrapServers = System.getenv("KAFKA_BOOTSTRAP_SERVERS") ?: "localhost:19092"
            val schemaRegistryUrl = System.getenv("SCHEMA_REGISTRY_URL") ?: "http://localhost:18081"

            return Config(
                bootstrapServers = bootstrapServers,
                schemaRegistryUrl = schemaRegistryUrl,
                topic = topic,
            )
        }
    }
}
