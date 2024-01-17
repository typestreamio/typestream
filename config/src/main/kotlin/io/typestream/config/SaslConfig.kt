package io.typestream.config

import kotlinx.serialization.Serializable

@Serializable
data class SaslConfig(val mechanism: String = "PLAIN", val jaasConfig: String)
