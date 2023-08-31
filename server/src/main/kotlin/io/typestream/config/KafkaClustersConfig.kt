package io.typestream.config

import io.typestream.konfig.KonfigSource

@KonfigSource("sources.kafka")
data class KafkaClustersConfig(val clusters: Map<String, KafkaConfig>)
