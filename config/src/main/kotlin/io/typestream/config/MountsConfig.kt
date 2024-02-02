package io.typestream.config

import kotlinx.serialization.Serializable
import net.peanuuutz.tomlkt.Toml
import net.peanuuutz.tomlkt.TomlTable

@Serializable
data class MountsConfig(val random: MutableMap<String, RandomConfig>) {
    companion object {

        fun from(mountConfig: String): MountsConfig {
            val toml = Toml.decodeFromString(TomlTable.serializer(), mountConfig)
            return from(toml)
        }

        fun from(configTable: TomlTable): MountsConfig {
            val randomMounts = mutableMapOf<String, RandomConfig>()
            val otherMounts = configTable["mounts"]
            if (otherMounts is TomlTable) {
                val randomMountsTable = otherMounts["random"]

                if (randomMountsTable is TomlTable) {
                    for (randomMount in randomMountsTable) {
                        randomMounts[randomMount.key] =
                            Toml.decodeFromTomlElement(RandomConfig.serializer(), randomMount.value)
                    }
                }
            }

            return MountsConfig(randomMounts)
        }
    }
}
