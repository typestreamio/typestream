package io.typestream.config

import io.github.oshai.kotlinlogging.KotlinLogging
import net.peanuuutz.tomlkt.Toml
import net.peanuuutz.tomlkt.TomlTable
import java.io.File
import java.net.InetAddress
import java.nio.file.Paths

data class Config(
    val sources: SourcesConfig,
    val grpc: GrpcConfig,
    val mounts: MountsConfig,
    val k8sMode: Boolean,
    val versionInfo: VersionInfo,
    val configPath: String,
) {
    companion object {
        private fun fetchAutoConfigFile(configPath: String): File {
            val autoConfigPath = Paths.get(configPath, "typestream.auto.toml")
            val configFile = autoConfigPath.toFile()

            if (!configFile.exists()) {
                configFile.createNewFile()
            }

            return configFile
        }


        private fun fetchAutoConfig(configPath: String): TomlTable {
            val configFile = fetchAutoConfigFile(configPath)

            return Toml.decodeFromString(TomlTable.serializer(), configFile.readText())
        }

        fun fetch(): Config {
            val logger = KotlinLogging.logger {}

            // Not convinced this should be public for now (which is why it's not officially documented)
            val systemConfigPath = SystemEnv["TYPESTREAM_SYSTEM_CONFIG_PATH"] ?: "/etc/typestream"

            var kubernetesMode = false
            try {
                InetAddress.getByName("kubernetes.default.svc")
                kubernetesMode = true
            } catch (_: Exception) {
            }

            if (kubernetesMode) {
                logger.info { "copy config from /config into /etc/typestream" }
                val configFile = Paths.get("/config", "typestream.toml").toFile()
                val systemConfigFile = Paths.get(systemConfigPath, "typestream.toml").toFile()

                if (configFile.exists()) {
                    configFile.copyTo(systemConfigFile, true)
                }
            }

            val envFile = SystemEnv["TYPESTREAM_CONFIG"]

            val configFilePath: String = if (envFile != null) {
                logger.info { "loading configuration from TYPESTREAM_CONFIG" }

                val parentDir = Paths.get(systemConfigPath).toFile()

                // Try to create the system config directory, fall back to temp if it fails
                val actualConfigPath = if (!parentDir.exists() && !parentDir.mkdirs()) {
                    logger.warn { "cannot write to $systemConfigPath, using temporary directory" }
                    val tempDir = Paths.get(System.getProperty("java.io.tmpdir"), "typestream").toFile()
                    tempDir.mkdirs()
                    tempDir.absolutePath
                } else {
                    systemConfigPath
                }

                val configFile = Paths.get(actualConfigPath, "typestream.toml").toFile()
                configFile.createNewFile()
                configFile.writeText(envFile)

                actualConfigPath
            } else {
                val paths =
                    SystemEnv["TYPESTREAM_CONFIG_PATH"] ?: ".:$systemConfigPath"

                val configFilePath = paths.split(":").map { Paths.get(it, "typestream.toml") }
                    .firstOrNull { it.toFile().exists() }

                if (configFilePath != null) {
                    logger.info { "loading configuration from $configFilePath" }
                    configFilePath.parent.toRealPath().toString()
                } else {
                    logger.info { "loading default configuration from resources" }
                    val fileStream = Config::class.java.getResourceAsStream("/typestream.toml")

                    require(fileStream != null) { "default configuration not found" }

                    val defaultPath = Paths.get(systemConfigPath, "typestream.toml")
                    val parentDir = Paths.get(systemConfigPath).toFile()

                    // Try to create the system config directory, fall back to temp if it fails
                    val actualConfigPath = if (!parentDir.exists() && !parentDir.mkdirs()) {
                        logger.warn { "cannot write to $systemConfigPath, using temporary directory" }
                        val tempDir = Paths.get(System.getProperty("java.io.tmpdir"), "typestream").toFile()
                        tempDir.mkdirs()
                        tempDir.absolutePath
                    } else {
                        systemConfigPath
                    }

                    val actualConfigFile = Paths.get(actualConfigPath, "typestream.toml").toFile()
                    actualConfigFile.createNewFile()
                    actualConfigFile.writeText(fileStream.readAllBytes().decodeToString().trimIndent())

                    actualConfigPath
                }
            }

            val serverConfig = Paths.get(configFilePath, "typestream.toml").toFile().inputStream()

            val versionInfo = VersionInfo.fetch()

            val tomlConfig = TomlConfig.from(serverConfig.readAllBytes().decodeToString())

            val autoConfigTable = fetchAutoConfig(configFilePath)

            logger.info { "loading auto configuration from $configFilePath/typestream.auto.toml" }
            val mountsConfig = MountsConfig.from(autoConfigTable)
            tomlConfig.mergeWith(mountsConfig)

            val config = Config(
                tomlConfig.sources,
                tomlConfig.grpc,
                tomlConfig.mounts,
                kubernetesMode,
                versionInfo,
                configFilePath
            )

            logger.info { "loaded configuration: $config" }

            return config
        }
    }

    fun mount(mountsConfig: MountsConfig) {
        mounts.random.putAll(mountsConfig.random)
        writeAutoConfigFile()
    }

    fun unmount(path: String) {
        mounts.random.entries.removeIf { it.value.endpoint == path }

        writeAutoConfigFile()
    }

    private fun writeAutoConfigFile() {
        val autoConfigFile = fetchAutoConfigFile(configPath)

        autoConfigFile.writeText("# THIS FILE IS AUTOGENERATED. DO NOT EDIT")
        autoConfigFile.appendText(
            Toml.encodeToString(
                AutoConfig.serializer(),
                AutoConfig(MountsConfig(mounts.random.toSortedMap()))
            )
        )
    }

}
