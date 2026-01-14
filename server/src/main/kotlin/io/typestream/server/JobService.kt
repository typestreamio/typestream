package io.typestream.server

import io.github.oshai.kotlinlogging.KotlinLogging
import java.util.concurrent.CancellationException
import io.typestream.compiler.Compiler
import io.typestream.compiler.GraphCompiler
import io.typestream.compiler.vm.Env
import io.typestream.compiler.vm.Session
import io.typestream.compiler.vm.Vm
import io.typestream.config.Config
import io.typestream.grpc.job_service.Job as ProtoJob
import io.typestream.grpc.job_service.Job.CreateJobRequest
import io.typestream.grpc.job_service.Job.CreateJobFromGraphRequest
import io.typestream.grpc.job_service.Job.ListJobsRequest
import io.typestream.grpc.job_service.Job.CreatePreviewJobRequest
import io.typestream.grpc.job_service.Job.StopPreviewJobRequest
import io.typestream.grpc.job_service.Job.StreamPreviewRequest
import io.typestream.grpc.job_service.Job.WatchJobsRequest
import io.typestream.grpc.job_service.JobServiceGrpcKt
import io.typestream.grpc.job_service.createJobResponse
import io.typestream.grpc.job_service.createPreviewJobResponse
import io.typestream.grpc.job_service.stopPreviewJobResponse
import io.typestream.grpc.job_service.streamPreviewResponse
import io.typestream.grpc.job_service.listJobsResponse
import io.typestream.grpc.job_service.jobInfo
import io.typestream.k8s.K8sClient
import io.typestream.kafka.KafkaAdminClient
import io.typestream.scheduler.Job as SchedulerJob
import io.typestream.scheduler.KafkaStreamsJob
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job as CoroutineJob
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.cancellable
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.Closeable
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration.Companion.minutes

data class PreviewJobInfo(
    val inspectorNodeId: String,
    val createdAt: Long = System.currentTimeMillis()
)

class JobService(private val config: Config, private val vm: Vm) :
    JobServiceGrpcKt.JobServiceCoroutineImplBase(), Closeable {

    private val logger = KotlinLogging.logger {}
    private val graphCompiler = GraphCompiler(vm.fileSystem)

    // Track preview jobs for cleanup: jobId -> PreviewJobInfo
    private val previewJobs = ConcurrentHashMap<String, PreviewJobInfo>()

    // TTL for preview jobs (cleanup if no client connected for this long)
    private val previewJobTtl = 10.minutes

    // Managed coroutine scope for background cleanup - can be cancelled on shutdown
    private val cleanupScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var cleanupJob: CoroutineJob? = null

    // Lazy admin client for topic cleanup
    private val kafkaAdminClient by lazy {
        val kafkaConfig = vm.fileSystem.config.sources.kafka.values.firstOrNull()
            ?: error("No Kafka source configured")
        KafkaAdminClient(kafkaConfig)
    }

    init {
        // Start background cleanup for orphaned preview jobs
        cleanupJob = cleanupScope.launch {
            while (isActive) {
                delay(1.minutes)
                cleanupExpiredPreviewJobs()
            }
        }
    }

    /**
     * Shuts down the background cleanup coroutine.
     * Should be called when the service is being stopped.
     */
    override fun close() {
        logger.info { "Shutting down JobService cleanup coroutine" }
        cleanupJob?.cancel()
        cleanupScope.cancel()
    }

    private fun cleanupExpiredPreviewJobs() {
        val now = System.currentTimeMillis()
        val expiredJobs = previewJobs.entries.filter { (_, info) ->
            now - info.createdAt > previewJobTtl.inWholeMilliseconds
        }

        expiredJobs.forEach { (jobId, _) ->
            logger.info { "Cleaning up expired preview job: $jobId" }
            cleanupPreviewJob(jobId)
        }
    }

    private fun cleanupPreviewJob(jobId: String) {
        val info = previewJobs.remove(jobId) ?: return

        try {
            vm.scheduler.kill(jobId)
        } catch (e: Exception) {
            logger.warn(e) { "Failed to kill preview job: $jobId" }
        }

        // Delete the inspector topic
        val inspectTopic = "$jobId-inspect-${info.inspectorNodeId}"
        try {
            kafkaAdminClient.deleteTopics(listOf(inspectTopic))
        } catch (e: Exception) {
            logger.warn(e) { "Failed to delete inspector topic: $inspectTopic" }
        }
    }

    override suspend fun createJob(request: CreateJobRequest): ProtoJob.CreateJobResponse = createJobResponse {
        //TODO we may want to generate uuids from the source code fingerprint
        // as it is, we'll have running apps on runtime (e.g. kafka streams) with a different id than the
        // job id that we create here
        val id = "typestream-app-${UUID.randomUUID()}"

        val compilerResult = Compiler(Session(vm.fileSystem, vm.scheduler, Env(config))).compile(request.source)

        this.success = compilerResult.errors.isEmpty()

        this.error = compilerResult.errors.joinToString("\n")

        if (compilerResult.errors.isEmpty()) {
            if (config.k8sMode) {
                K8sClient().use {
                    it.createWorkerJob(config.versionInfo.version, id, request.source)
                }
            } else {
                vm.run(request.source, Session(vm.fileSystem, vm.scheduler, Env(config)))
            }
        }

        this.jobId = id
    }

    override suspend fun createJobFromGraph(request: CreateJobFromGraphRequest): ProtoJob.CreateJobResponse = createJobResponse {
        try {
            val program = graphCompiler.compile(request)

            this.success = true
            this.jobId = program.id
            this.error = ""

            // Pass program to scheduler (same path as text compiler)
            if (config.k8sMode) {
                // TODO: Phase 4 - Serialize program to JSON for K8s worker
                // For now, K8s mode is not supported for graph-based jobs
                this.success = false
                this.error = "K8s mode not yet supported for graph-based jobs (Phase 4)"
            } else {
                vm.runProgram(program, Session(vm.fileSystem, vm.scheduler, Env(config)))
            }
        } catch (e: Exception) {
            this.success = false
            this.jobId = ""
            this.error = e.message ?: "Unknown error during graph compilation"
        }
    }

    override suspend fun listJobs(request: ListJobsRequest): ProtoJob.ListJobsResponse = listJobsResponse {
        try {
            // Get list of running jobs from scheduler
            val runningJobs = vm.scheduler.ps()

            runningJobs.forEach { schedulerJob ->
                // Skip preview jobs from the listing
                if (previewJobs.containsKey(schedulerJob.id)) return@forEach

                jobs.add(jobInfo {
                    jobId = schedulerJob.id
                    state = mapJobState(schedulerJob.state())
                    startTime = schedulerJob.startTime
                    // Include graph if available (only for graph-based jobs)
                    if (schedulerJob is KafkaStreamsJob) {
                        schedulerJob.program.pipelineGraph?.let { graph = it }
                    }
                })
            }
        } catch (e: Exception) {
            logger.error(e) { "Error listing jobs" }
            // Return empty list on error
        }
    }

    override suspend fun createPreviewJob(request: CreatePreviewJobRequest): ProtoJob.CreatePreviewJobResponse = createPreviewJobResponse {
        try {
            val program = graphCompiler.compile(
                ProtoJob.CreateJobFromGraphRequest.newBuilder()
                    .setUserId("preview")
                    .setGraph(request.graph)
                    .build()
            )

            val inspectTopic = "${program.id}-inspect-${request.inspectorNodeId}"

            if (config.k8sMode) {
                this.success = false
                this.error = "Preview jobs not supported in K8s mode"
            } else {
                vm.runProgram(program, Session(vm.fileSystem, vm.scheduler, Env(config)))
                previewJobs[program.id] = PreviewJobInfo(request.inspectorNodeId)
                this.success = true
                this.jobId = program.id
                this.inspectTopic = inspectTopic
            }
        } catch (e: Exception) {
            logger.error(e) { "Error creating preview job" }
            this.success = false
            this.error = e.message ?: "Unknown error creating preview job"
        }
    }

    override suspend fun stopPreviewJob(request: StopPreviewJobRequest): ProtoJob.StopPreviewJobResponse = stopPreviewJobResponse {
        try {
            val jobId = request.jobId
            if (previewJobs.containsKey(jobId)) {
                cleanupPreviewJob(jobId)
                this.success = true
            } else {
                this.success = false
                this.error = "Preview job not found: $jobId"
            }
        } catch (e: Exception) {
            logger.error(e) { "Error stopping preview job" }
            this.success = false
            this.error = e.message ?: "Unknown error stopping preview job"
        }
    }

    override fun streamPreview(request: StreamPreviewRequest): Flow<ProtoJob.StreamPreviewResponse> = flow {
        val jobId = request.jobId
        val info = previewJobs[jobId] ?: error("Preview job not found: $jobId")
        val inspectTopic = "$jobId-inspect-${info.inspectorNodeId}"

        try {
            // Consume from the inspector topic
            // Use cancellable() to ensure the flow responds to cancellation signals
            vm.scheduler.jobOutput(jobId, inspectTopic).cancellable().collect { output ->
                // Check for cancellation before each emit (ensures we respond to client disconnect)
                currentCoroutineContext().ensureActive()
                emit(streamPreviewResponse {
                    this.value = output
                    this.timestamp = System.currentTimeMillis()
                })
            }
        } finally {
            // Clean up when stream ends (client disconnect, error, or normal completion)
            if (previewJobs.containsKey(jobId)) {
                logger.info { "Stream ended for preview job $jobId, cleaning up" }
                cleanupPreviewJob(jobId)
            }
        }
    }

    override fun watchJobs(request: WatchJobsRequest): Flow<ProtoJob.JobInfo> = flow {
        // Track previously seen jobs to detect changes
        var previousJobs = mapOf<String, SchedulerJob.State>()

        while (currentCoroutineContext().isActive) {
            try {
                val currentJobs = vm.scheduler.ps()
                val currentJobMap = currentJobs.associate { it.id to it.state() }

                // Emit all jobs on state change or new job
                val hasChanges = currentJobMap != previousJobs

                if (hasChanges) {
                    currentJobs.forEach { schedulerJob ->
                        // Skip preview jobs from the watch stream
                        if (previewJobs.containsKey(schedulerJob.id)) return@forEach

                        emit(jobInfo {
                            jobId = schedulerJob.id
                            state = mapJobState(schedulerJob.state())
                            startTime = schedulerJob.startTime
                            // Include graph if available (only for graph-based jobs)
                            if (schedulerJob is KafkaStreamsJob) {
                                schedulerJob.program.pipelineGraph?.let { graph = it }
                            }
                        })
                    }
                    previousJobs = currentJobMap
                }

                // Poll every second
                delay(1000)
            } catch (e: CancellationException) {
                // Client disconnected - expected, exit gracefully
                break
            } catch (e: Exception) {
                logger.error(e) { "Error watching jobs" }
                delay(1000) // Continue polling even on error
            }
        }
    }

    private fun mapJobState(state: SchedulerJob.State): ProtoJob.JobState = when (state) {
        SchedulerJob.State.STARTING -> ProtoJob.JobState.STARTING
        SchedulerJob.State.RUNNING -> ProtoJob.JobState.RUNNING
        SchedulerJob.State.STOPPING -> ProtoJob.JobState.STOPPING
        SchedulerJob.State.STOPPED -> ProtoJob.JobState.STOPPED
        SchedulerJob.State.FAILED -> ProtoJob.JobState.FAILED
        SchedulerJob.State.UNKNOWN -> ProtoJob.JobState.UNKNOWN
    }
}
