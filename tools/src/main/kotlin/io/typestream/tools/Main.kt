package io.typestream.tools

import io.github.oshai.kotlinlogging.KotlinLogging
import io.typestream.config.Config
import io.typestream.tools.command.seed
import io.typestream.tools.command.streamPageViews


class Main(private val config: Config) {
    fun run(command: String, args: List<String>) {
        when (command) {
            "seed" -> seed(config, args)
            "stream-page-views" -> streamPageViews(config, args)
            else -> error("cannot run $command")
        }
    }

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            val logger = KotlinLogging.logger {}
            val command = args.firstOrNull() ?: error("command is required")

            val config = Config.fetch()

            logger.info { "\uD83D\uDE80 starting tools ${config.versionInfo}" }
            logger.info { "\uD83C\uDFC3\u200D♂\uFE0F running $command with ${args.toList()}" }

            val app = Main(config)

            app.run(command, args.drop(1))
        }
    }
}


