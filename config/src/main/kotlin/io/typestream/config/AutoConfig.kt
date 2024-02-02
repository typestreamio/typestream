package io.typestream.config

import kotlinx.serialization.Serializable

@Serializable
data class AutoConfig(val mounts: MountsConfig)
