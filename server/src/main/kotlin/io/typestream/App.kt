package io.typestream

import io.github.oshai.kotlinlogging.KotlinLogging
import io.grpc.Server
import io.grpc.ServerBuilder
import io.grpc.protobuf.services.ProtoReflectionService
import io.typestream.config.GrpcConfig
import io.typestream.config.SourcesConfig
import io.typestream.filesystem.FileSystem
import io.typestream.scheduler.Scheduler
import io.typestream.server.ExceptionInterceptor
import io.typestream.server.ProgramService
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.io.Closeable
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.InputStream
import kotlin.system.exitProcess


class App(konfig: io.typestream.konfig.Konfig, private val dispatcher: CoroutineDispatcher = Dispatchers.IO) :
    Closeable {
    private val logger = KotlinLogging.logger {}

    var server: Server? = null

    private val grpcConfig: GrpcConfig by konfig.inject()
    private val sourcesConfig = SourcesConfig(konfig)

    private val subSystems = mutableListOf<Closeable>()

    fun run(serverBuilder: ServerBuilder<*> = ServerBuilder.forPort(grpcConfig.port)) = runBlocking {
        val fileSystem = FileSystem(sourcesConfig, dispatcher)

        subSystems.add(fileSystem)
        launch(dispatcher) {
            fileSystem.watch()
        }
        logger.info { "file system watching for changes" }

        val scheduler = Scheduler(sourcesConfig, dispatcher = dispatcher)
        subSystems.add(scheduler)

        launch(dispatcher) {
            scheduler.start()
        }

        serverBuilder.intercept(ExceptionInterceptor())
        serverBuilder.addService(ProgramService(fileSystem, scheduler))
        serverBuilder.addService(ProtoReflectionService.newInstance())
        //TODO add health check. See https://github.com/grpc/grpc/blob/master/doc/health-checking.md

        server = serverBuilder.build()
        server?.start()
        logger.info { "grpc server listening on port ${server?.port}" }
        server?.awaitTermination()
    }

    override fun close() {
        logger.info { "shutting down grpc server" }
        server?.shutdown()

        logger.info { "shutting down sub-systems" }
        subSystems.forEach(Closeable::close)
    }


    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            val logger = KotlinLogging.logger {}
            val serverConfig: InputStream? = if (args.isEmpty()) {
                this::class.java.getResourceAsStream("/server.properties")
            } else {
                try {
                    FileInputStream(args[0])
                } catch (e: FileNotFoundException) {
                    null
                }
            }
            if (serverConfig == null) {
                logger.info { "cannot load configuration" }
                exitProcess(1)
            }

            val konfig = io.typestream.konfig.Konfig(serverConfig)

            val app = App(konfig)

            Runtime.getRuntime().addShutdownHook(object : Thread() {
                override fun run() {
                    logger.info { "shutting down gRPC server since JVM is shutting down" }
                    try {
                        app.close()
                    } catch (e: InterruptedException) {
                        e.printStackTrace(System.err)
                    }
                    logger.info { "server shut down" }
                }
            })

            val versionInfo = io.typestream.version_info.VersionInfo.get()

            logger.info { "starting TypeStream server $versionInfo" }

            app.run()
        }
    }
}


