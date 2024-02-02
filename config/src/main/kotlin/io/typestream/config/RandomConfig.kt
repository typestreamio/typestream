package io.typestream.config

import kotlinx.serialization.Serializable

@Serializable
data class RandomConfig(val valueType: String, val endpoint: String)
