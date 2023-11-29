package io.typestream.config

data class KafkaConfig(
    val bootstrapServers: String,
    val fsRefreshRate: Int = 60,
    val schemaRegistry: SchemaRegistryConfig,
)
