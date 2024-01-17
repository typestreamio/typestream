package io.typestream

import io.github.oshai.kotlinlogging.KotlinLogging
import io.typestream.config.Config

fun main() {
    val logger = KotlinLogging.logger {}

    val config = Config.fetch()
    val workerMode = System.getenv("WORKER_ID") != null

    if (workerMode) {
        logger.info { "running TypeStream (${config.versionInfo}) in worker mode" }
        return Worker(config).run()
    }

    logger.info { "running in Typestream (${config.versionInfo}) in server mode" }

    val server = Server(config)

    Runtime.getRuntime().addShutdownHook(object : Thread() {
        override fun run() {
            logger.info { "shutting down gRPC server since JVM is shutting down" }
            try {
                server.close()
            } catch (e: InterruptedException) {
                e.printStackTrace(System.err)
            }
            logger.info { "server shut down" }
        }
    })

    server.run()
}
