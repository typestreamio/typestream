package io.typestream.config.testing

import io.typestream.config.Config
import io.typestream.config.TomlConfig
import io.typestream.config.VersionInfo
import io.typestream.testing.testConfigFile
import org.testcontainers.redpanda.RedpandaContainer

fun testConfig(testKafka: RedpandaContainer): Config {
    val tomlConfig = TomlConfig.from(testConfigFile(testKafka))

    return Config(
        tomlConfig.sources,
        tomlConfig.grpc,
        false,
        VersionInfo("beta", "n/a")
    )
}
