package io.typestream

import io.github.oshai.kotlinlogging.KotlinLogging
import io.typestream.k8s.K8sClient
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.InputStream
import java.net.InetAddress
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    val logger = KotlinLogging.logger {}
    val versionInfo = io.typestream.version_info.VersionInfo.get()

    var kubernetesMode = false
    try {
        InetAddress.getByName("kubernetes.default.svc")
        kubernetesMode = true
    } catch (_: Exception) {
    }

    val serverConfig: InputStream? = if (kubernetesMode) {
        logger.info { "running in kubernetes mode" }

        val k8sClient = K8sClient()

        logger.info { "fetching config" }

        k8sClient.use { it.getServerProperties() }
    } else {
        logger.info { "running in local mode" }
        if (args.isEmpty()) {
            Server::class.java.getResourceAsStream("/server.properties")
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
    val konfig = io.typestream.konfig.Konfig(serverConfig)

    val workerMode = System.getenv("WORKER_ID") != null

    if (workerMode) {
        logger.info { "running TypeStream ($versionInfo) in worker mode" }
        return Worker(konfig).run()
    }

    logger.info { "running in Typestream ($versionInfo) in server mode" }

    val server = Server(konfig, versionInfo)

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

    server.run(kubernetesMode)
}
