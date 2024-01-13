package io.typestream.config

import kotlinx.serialization.Serializable

@Serializable
data class GrpcConfig(val port: Int)
