package io.typestream

import io.github.oshai.kotlinlogging.KotlinLogging
import io.grpc.Server
import io.grpc.ServerBuilder
import io.grpc.protobuf.services.ProtoReflectionService
import io.typestream.compiler.vm.Vm
import io.typestream.config.GrpcConfig
import io.typestream.config.SourcesConfig
import io.typestream.filesystem.FileSystem
import io.typestream.konfig.Konfig
import io.typestream.scheduler.Scheduler
import io.typestream.server.ExceptionInterceptor
import io.typestream.server.InteractiveSessionService
import io.typestream.server.JobService
import io.typestream.server.LoggerInterceptor
import io.typestream.version_info.VersionInfo
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.io.Closeable


class Server(
    konfig: Konfig,
    private val versionInfo: VersionInfo,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) :
    Closeable {
    private val logger = KotlinLogging.logger {}

    var server: Server? = null

    private val grpcConfig: GrpcConfig by konfig.inject()
    private val sourcesConfig = SourcesConfig(konfig)

    private val subSystems = mutableListOf<Closeable>()

    fun run(k8sMode: Boolean, serverBuilder: ServerBuilder<*> = ServerBuilder.forPort(grpcConfig.port)) = runBlocking {
        val fileSystem = FileSystem(sourcesConfig, dispatcher)

        subSystems.add(fileSystem)
        launch(dispatcher) {
            fileSystem.watch()
        }
        logger.info { "file system watching for changes" }

        val scheduler = Scheduler(k8sMode, dispatcher)

        subSystems.add(scheduler)
        launch(dispatcher) {
            scheduler.start()
        }

        val vm = Vm(fileSystem, scheduler)

        serverBuilder.intercept(ExceptionInterceptor())
        serverBuilder.intercept(LoggerInterceptor())
        serverBuilder.addService(InteractiveSessionService(vm))
        serverBuilder.addService(JobService(versionInfo.version, k8sMode, vm))
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
}
