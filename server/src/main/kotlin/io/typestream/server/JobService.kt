package io.typestream.server

import io.github.oshai.kotlinlogging.KotlinLogging
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
import io.typestream.grpc.job_service.JobServiceGrpcKt
import io.typestream.grpc.job_service.createJobResponse
import io.typestream.grpc.job_service.listJobsResponse
import io.typestream.grpc.job_service.jobInfo
import io.typestream.grpc.job_service.jobThroughput
import io.typestream.k8s.K8sClient
import io.typestream.scheduler.Job
import java.util.UUID

class JobService(private val config: Config, private val vm: Vm) :
    JobServiceGrpcKt.JobServiceCoroutineImplBase() {

    private val logger = KotlinLogging.logger {}
    private val graphCompiler = GraphCompiler(vm.fileSystem)

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
                jobs.add(jobInfo {
                    jobId = schedulerJob.id
                    state = when (schedulerJob.state()) {
                        Job.State.STARTING -> ProtoJob.JobState.STARTING
                        Job.State.RUNNING -> ProtoJob.JobState.RUNNING
                        Job.State.STOPPING -> ProtoJob.JobState.STOPPING
                        Job.State.STOPPED -> ProtoJob.JobState.STOPPED
                        Job.State.FAILED -> ProtoJob.JobState.FAILED
                        Job.State.UNKNOWN -> ProtoJob.JobState.UNKNOWN
                    }
                    startTime = schedulerJob.startTime
                    // Include graph if available (only for graph-based jobs)
                    if (schedulerJob is io.typestream.scheduler.KafkaStreamsJob) {
                        schedulerJob.program.pipelineGraph?.let { graph = it }
                    }
                    // Include throughput metrics
                    val jobThroughputData = schedulerJob.throughput()
                    throughput = jobThroughput {
                        messagesPerSecond = jobThroughputData.messagesPerSecond
                        totalMessages = jobThroughputData.totalMessages
                    }
                })
            }
        } catch (e: Exception) {
            logger.error(e) { "Error listing jobs" }
            // Return empty list on error
        }
    }
}
