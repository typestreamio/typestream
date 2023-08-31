package io.typestream.config

data class KafkaConfig(val bootstrapServers: String, val schemaRegistryUrl: String, val fsRefreshRate: Int = 60)
