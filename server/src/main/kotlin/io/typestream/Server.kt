package io.typestream

import io.github.oshai.kotlinlogging.KotlinLogging
import io.grpc.Server
import io.grpc.ServerBuilder
import io.grpc.protobuf.services.ProtoReflectionService
import io.typestream.compiler.vm.Vm
import io.typestream.config.Config
import io.typestream.filesystem.FileSystem
import io.typestream.scheduler.Scheduler
import io.typestream.server.ExceptionInterceptor
import io.typestream.server.FileSystemService
import io.typestream.server.InteractiveSessionService
import io.typestream.server.JobService
import io.typestream.server.LoggerInterceptor
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.io.Closeable

class Server(private val config: Config, private val dispatcher: CoroutineDispatcher = Dispatchers.IO) : Closeable {
    private val logger = KotlinLogging.logger {}

    var server: Server? = null

    private val subSystems = mutableListOf<Closeable>()

    fun run(serverBuilder: ServerBuilder<*> = ServerBuilder.forPort(config.grpc.port)) = runBlocking {
        val fileSystem = FileSystem(config, dispatcher)

        subSystems.add(fileSystem)
        launch(dispatcher) {
            fileSystem.watch()
        }
        logger.info { "file system watching for changes" }

        val scheduler = Scheduler(config.k8sMode, dispatcher)

        subSystems.add(scheduler)
        launch(dispatcher) {
            scheduler.start()
        }

        val vm = Vm(fileSystem, scheduler)

        serverBuilder.intercept(ExceptionInterceptor())
        serverBuilder.intercept(LoggerInterceptor())

        serverBuilder.addService(FileSystemService(vm))
        serverBuilder.addService(InteractiveSessionService(config, vm))
        val jobService = JobService(config, vm)
        subSystems.add(jobService)
        serverBuilder.addService(jobService)

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
