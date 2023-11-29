package io.typestream.config

import io.typestream.konfig.KonfigSource

@KonfigSource("schemaRegistry")
data class SchemaRegistryConfig(val url: String, val userInfo: String? = null) {
    companion object {
        fun fromMap(configs: Map<String, *>): SchemaRegistryConfig {
            return SchemaRegistryConfig(
                configs["schema.registry.url"] as String,
                configs["schema.registry.userInfo"] as String?,
            )
        }
    }
}
