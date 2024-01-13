package io.typestream.config

import kotlinx.serialization.Serializable

@Serializable
data class SourcesConfig(val kafka: Map<String, KafkaConfig>)
