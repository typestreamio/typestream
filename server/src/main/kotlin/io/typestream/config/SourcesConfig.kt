package io.typestream.config

class SourcesConfig(konfig: io.typestream.konfig.Konfig) {
    val kafkaClustersConfig: KafkaClustersConfig by konfig.inject()
}
