package io.typestream.config

import kotlinx.serialization.Serializable
import net.peanuuutz.tomlkt.Toml

@Serializable
data class TomlConfig(val sources: SourcesConfig, val grpc: GrpcConfig) {
    companion object {
        fun from(input: String) = Toml.decodeFromString(serializer(), input)
    }
}
