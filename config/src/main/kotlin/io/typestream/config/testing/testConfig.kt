package io.typestream.config.testing

import io.typestream.config.Config
import io.typestream.config.TomlConfig
import io.typestream.config.VersionInfo
import org.testcontainers.redpanda.RedpandaContainer

private fun testConfigFile(testKafka: RedpandaContainer) = """
[grpc]
port=0
[sources.kafka.local]
bootstrapServers="${testKafka.bootstrapServers}"
schemaRegistry.url="${testKafka.schemaRegistryAddress}"
fsRefreshRate=1
""".trimIndent()

fun testConfig(testKafka: RedpandaContainer): Config {
    val tomlConfig = TomlConfig.from(testConfigFile(testKafka))

    return Config(
        tomlConfig.sources,
        tomlConfig.grpc,
        tomlConfig.mounts,
        false,
        VersionInfo("beta", "n/a"),
        "/etc/typestream"
    )
}
