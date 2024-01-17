package io.typestream.config

import kotlinx.serialization.Serializable

@Serializable
data class KafkaConfig(
    val bootstrapServers: String,
    val fsRefreshRate: Int = 60,
    val schemaRegistry: SchemaRegistryConfig,
    val saslConfig: SaslConfig? = null,
)
