package io.typestream.tools

import io.fabric8.kubernetes.client.ConfigBuilder
import io.fabric8.kubernetes.client.KubernetesClientBuilder
import io.github.oshai.kotlinlogging.KotlinLogging
import io.typestream.tools.command.seed
import io.typestream.tools.command.streamPageViews
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.InputStream
import java.net.InetAddress
import kotlin.system.exitProcess


class Main(konfig: io.typestream.konfig.Konfig) {
    private val config: Config = Config(konfig)
    private val logger = KotlinLogging.logger {}

    fun run(command: String) {

        when (command) {
            "seed" -> {
                seed(config.kafkaClustersConfig)
            }

            "stream-page-views" -> {
                streamPageViews(config.kafkaClustersConfig)
            }

            else -> error("cannot run $command")
        }
    }

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            val logger = KotlinLogging.logger {}
            val command = if (args.size == 1) {
                args[0]
            } else {
                args[1]
            }

            var kubernetesMode = false
            try {
                InetAddress.getByName("kubernetes.default.svc")
                kubernetesMode = true
            } catch (_: Exception) {
            }

            val serverConfig: InputStream? = if (kubernetesMode) {
                logger.info { "running in kubernetes mode" }

                val kubeConfig = ConfigBuilder()
                    .withConnectionTimeout(100)
                    .withRequestTimeout(100)
                    .build()
                val client = KubernetesClientBuilder().withConfig(kubeConfig).build()

                logger.info { "fetching config" }

                client.use {
                    val configMap = client.configMaps()
                        .inNamespace("typestream")
                        .withName("server-config")
                        .get()
                    logger.info { "config: ${configMap.data["server.properties"]}" }

                    if (configMap != null) {
                        configMap.data["server.properties"]?.byteInputStream()
                    } else {
                        null
                    }
                }
            } else {
                logger.info { "running in local mode" }
                if (args.size == 1) {
                    this::class.java.getResourceAsStream("/tools.properties")
                } else {
                    try {
                        FileInputStream(args[0])
                    } catch (e: FileNotFoundException) {
                        null
                    }
                }
            }

            if (serverConfig == null) {
                logger.info { "cannot load configuration" }
                exitProcess(1)
            }

            val versionInfo = io.typestream.version_info.VersionInfo.get()
            logger.info { "\uD83D\uDE80 starting tools $versionInfo" }
            logger.info { "\uD83C\uDFC3\u200D♂\uFE0F running $command" }

            val app = Main(io.typestream.konfig.Konfig(serverConfig))

            app.run(command)
        }
    }
}


