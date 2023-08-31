package io.typestream.config

import io.typestream.konfig.KonfigSource

@KonfigSource("grpc")
data class GrpcConfig(val port: Int)
