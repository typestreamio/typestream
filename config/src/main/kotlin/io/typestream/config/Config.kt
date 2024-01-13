package io.typestream.config

import io.github.oshai.kotlinlogging.KotlinLogging
import io.typestream.k8s.K8sClient
import java.io.InputStream
import java.net.InetAddress
import java.nio.file.Paths
import kotlin.system.exitProcess


data class Config(
    val sources: SourcesConfig,
    val grpc: GrpcConfig,
    val k8sMode: Boolean,
    val versionInfo: VersionInfo,
) {
    companion object {
        fun fetch(): Config {
            val logger = KotlinLogging.logger {}

            var kubernetesMode = false
            try {
                InetAddress.getByName("kubernetes.default.svc")
                kubernetesMode = true
            } catch (_: Exception) {
            }

            val envFile = System.getenv("TYPESTREAM_CONFIG")

            val serverConfig: InputStream? = if (envFile != null) {
                logger.info { "loading configuration from TYPESTREAM_CONFIG" }
                logger.info { "configuration: $envFile" }

                envFile.byteInputStream()
            } else if (kubernetesMode) {
                val k8sClient = K8sClient()

                logger.info { "fetching config via config map" }

                k8sClient.use { it.getServerConfig() }
            } else {
                val paths =
                    System.getenv("TYPESTREAM_CONFIG_PATH") ?: ".:${System.getProperty("user.home")}:/etc/typestream"

                val configFilePath = paths.split(":").map { Paths.get(it, "typestream.toml") }
                    .firstOrNull { it.toFile().exists() }

                if (configFilePath != null) {
                    logger.info { "loading configuration from $configFilePath" }
                    configFilePath.toFile().inputStream()
                } else {
                    Config::class.java.getResourceAsStream("/typestream.toml")
                }
            }

            if (serverConfig == null) {
                logger.info { "cannot load configuration" }
                exitProcess(1)
            }

            val versionInfo = VersionInfo.fetch()

            val tomlConfig = TomlConfig.from(serverConfig.readAllBytes().decodeToString())

            return Config(tomlConfig.sources, tomlConfig.grpc, kubernetesMode, versionInfo)
        }
    }
}
