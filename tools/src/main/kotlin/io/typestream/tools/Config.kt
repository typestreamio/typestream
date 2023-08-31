package io.typestream.tools

import io.typestream.konfig.KonfigSource


data class KafkaConfig(val bootstrapServers: String, val schemaRegistryUrl: String)

@KonfigSource("sources.kafka")
data class KafkaClustersConfig(val clusters: Map<String, KafkaConfig>)

class Config(konfig: io.typestream.konfig.Konfig) {
    val kafkaClustersConfig: KafkaClustersConfig by konfig.inject()
}
