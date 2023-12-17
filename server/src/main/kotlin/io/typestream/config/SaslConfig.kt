package io.typestream.config

import io.typestream.konfig.KonfigSource

@KonfigSource("sasl")
data class SaslConfig(val mechanism: String = "PLAIN", val jaasConfig: String)
