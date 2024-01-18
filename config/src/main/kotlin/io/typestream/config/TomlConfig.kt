package io.typestream.config

import kotlinx.serialization.Serializable
import net.peanuuutz.tomlkt.Toml
import net.peanuuutz.tomlkt.TomlTable

@Serializable
data class TomlConfig(val sources: SourcesConfig, val grpc: GrpcConfig, val mounts: MountsConfig) {
    fun mergeWith(mountsConfig: MountsConfig) {
        mounts.random.putAll(mountsConfig.random)
    }

    companion object {
        fun from(input: String): TomlConfig {
            val toml = Toml.decodeFromString(TomlTable.serializer(), input)

            val tomlSources = toml["sources"]

            require(tomlSources is TomlTable) { "sources must be a toml table" }
            val sources = Toml.decodeFromTomlElement(SourcesConfig.serializer(), tomlSources)

            val tomlGrpc = toml["grpc"]
            require(tomlGrpc is TomlTable) { "grpc must be a toml table" }

            val grpc = Toml.decodeFromTomlElement(GrpcConfig.serializer(), tomlGrpc)

            val mounts = if (toml["mounts"] != null) {
                Toml.decodeFromTomlElement(MountsConfig.serializer(), toml["mounts"]!!)
            } else {
                MountsConfig(random = mutableMapOf())
            }


            return TomlConfig(sources, grpc, mounts)
        }
    }
}
