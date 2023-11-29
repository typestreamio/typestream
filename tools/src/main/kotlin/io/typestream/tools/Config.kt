package io.typestream.tools

import io.typestream.konfig.Konfig
import io.typestream.konfig.KonfigSource


@KonfigSource("schemaRegistry")
data class SchemaRegistryConfig(val url: String, val userInfo: String?)

data class KafkaConfig(val bootstrapServers: String, val schemaRegistry: SchemaRegistryConfig)

@KonfigSource("sources.kafka")
data class KafkaClustersConfig(val clusters: Map<String, KafkaConfig>)

class Config(konfig: Konfig) {
    val kafkaClustersConfig: KafkaClustersConfig by konfig.inject()
}
