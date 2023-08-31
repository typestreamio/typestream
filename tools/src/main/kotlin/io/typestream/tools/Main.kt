package io.typestream.tools

import io.github.oshai.kotlinlogging.KotlinLogging
import io.typestream.tools.command.seed
import io.typestream.tools.command.streamPageViews
import java.io.FileInputStream


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

            val configFile = if (args.size == 1) {
                this::class.java.getResourceAsStream("/tools.properties")
            } else {
                FileInputStream(args[0])
            }

            val versionInfo = io.typestream.version_info.VersionInfo.get()
            logger.info { "\uD83D\uDE80 starting tools $versionInfo" }
            logger.info { "\uD83C\uDFC3\u200D♂\uFE0F running $command" }

            val app = Main(io.typestream.konfig.Konfig(configFile))

            app.run(command)
        }
    }
}


