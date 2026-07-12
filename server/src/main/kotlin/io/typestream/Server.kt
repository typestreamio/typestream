package io.typestream

import io.github.oshai.kotlinlogging.KotlinLogging
import io.grpc.Server
import io.grpc.ServerBuilder
import io.grpc.protobuf.services.ProtoReflectionService
import io.grpc.services.HealthStatusManager
import io.typestream.compiler.vm.Vm
import io.typestream.config.Config
import io.typestream.filesystem.FileSystem
import io.typestream.health.HealthMonitor
import io.typestream.scheduler.Scheduler
import io.typestream.server.ExceptionInterceptor
import io.typestream.server.ConnectionService
import io.typestream.server.FileSystemService
import io.typestream.server.InteractiveSessionService
import io.typestream.server.JobService
import io.typestream.server.LoggerInterceptor
import io.typestream.server.PipelineService
import io.typestream.server.StateQueryService
import io.typestream.pipeline.PipelineStateStore
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.io.Closeable
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class Server(
    private val config: Config,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val healthGraceWindow: Duration = 15.minutes,
    private val healthPollInterval: Duration = 15.seconds,
) : Closeable {
    private val logger = KotlinLogging.logger {}

    var server: Server? = null

    private val subSystems = mutableListOf<Closeable>()

    // The health monitor is a long-running loop (not a Closeable), so close() must cancel it
    // explicitly; otherwise run()'s runBlocking never completes after shutdown (it joins this child).
    private var healthMonitorJob: Job? = null

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
        val connectionService = ConnectionService()
        subSystems.add(connectionService)
        serverBuilder.addService(connectionService)
        val jobService = JobService(config, vm, connectionService)
        subSystems.add(jobService)
        serverBuilder.addService(jobService)
        val kafkaConfig = fileSystem.config.sources.kafka.values.firstOrNull()
        val stateStore = if (kafkaConfig != null) {
            try {
                PipelineStateStore(kafkaConfig)
            } catch (e: Exception) {
                throw IllegalStateException(
                    "cannot start: Kafka is unreachable at ${kafkaConfig.bootstrapServers}", e
                )
            }
        } else null
        val pipelineService = PipelineService(config, vm, stateStore, connectionService)
        subSystems.add(pipelineService)
        serverBuilder.addService(pipelineService)
        if (stateStore != null) {
            pipelineService.recoverPipelines()
        }
        serverBuilder.addService(StateQueryService(vm))

        serverBuilder.addService(ProtoReflectionService.newInstance())

        // Surface pipeline health over the standard gRPC health protocol so a k8s liveness probe
        // can restart a pod whose pipelines have failed or wedged.
        // See https://github.com/grpc/grpc/blob/master/doc/health-checking.md
        val health = HealthStatusManager()
        serverBuilder.addService(health.healthService)
        val healthMonitor = HealthMonitor(
            jobStates = { scheduler.ps().map { it.id to it.state() } },
            healthManager = health,
            graceWindow = healthGraceWindow,
            interval = healthPollInterval,
        )
        healthMonitorJob = launch(dispatcher) {
            healthMonitor.run()
        }

        server = serverBuilder.build()
        server?.start()
        logger.info { "grpc server listening on port ${server?.port}" }
        server?.awaitTermination()
    }

    override fun close() {
        logger.info { "shutting down grpc server" }
        healthMonitorJob?.cancel()
        server?.shutdown()

        logger.info { "shutting down sub-systems" }
        subSystems.forEach(Closeable::close)
    }
}
