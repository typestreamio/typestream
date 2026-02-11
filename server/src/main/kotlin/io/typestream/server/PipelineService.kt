package io.typestream.server

import io.github.oshai.kotlinlogging.KotlinLogging
import io.typestream.compiler.GraphCompiler
import io.typestream.compiler.vm.Env
import io.typestream.compiler.vm.Session
import io.typestream.compiler.vm.Vm
import io.typestream.config.Config
import io.typestream.grpc.job_service.Job as ProtoJob
import io.typestream.grpc.pipeline_service.Pipeline
import io.typestream.grpc.pipeline_service.PipelineServiceGrpcKt
import io.typestream.grpc.pipeline_service.applyPipelineResponse
import io.typestream.grpc.pipeline_service.deletePipelineResponse
import io.typestream.grpc.pipeline_service.listPipelinesResponse
import io.typestream.grpc.pipeline_service.pipelineInfo
import io.typestream.grpc.pipeline_service.validatePipelineResponse
import io.typestream.scheduler.KafkaStreamsJob
import java.util.concurrent.ConcurrentHashMap

data class ManagedPipeline(
    val metadata: Pipeline.PipelineMetadata,
    val graph: ProtoJob.PipelineGraph,
    val jobId: String,
    val appliedAt: Long = System.currentTimeMillis()
)

class PipelineService(
    private val config: Config,
    private val vm: Vm
) : PipelineServiceGrpcKt.PipelineServiceCoroutineImplBase() {

    private val logger = KotlinLogging.logger {}
    private val graphCompiler = GraphCompiler(vm.fileSystem)
    private val managedPipelines = ConcurrentHashMap<String, ManagedPipeline>()

    override suspend fun validatePipeline(
        request: Pipeline.ValidatePipelineRequest
    ): Pipeline.ValidatePipelineResponse = validatePipelineResponse {
        try {
            val name = request.metadata.name
            if (name.isBlank()) {
                errors.add("Pipeline name is required")
                valid = false
                return@validatePipelineResponse
            }

            // Dry-run compile to check for errors
            graphCompiler.compileFromGraph(request.graph, "typestream-pipeline-$name")

            valid = true
        } catch (e: Exception) {
            valid = false
            errors.add(e.message ?: "Unknown validation error")
        }
    }

    override suspend fun applyPipeline(
        request: Pipeline.ApplyPipelineRequest
    ): Pipeline.ApplyPipelineResponse = applyPipelineResponse {
        try {
            val name = request.metadata.name
            if (name.isBlank()) {
                success = false
                error = "Pipeline name is required"
                return@applyPipelineResponse
            }

            val deterministicId = "typestream-pipeline-$name"

            // Check if pipeline already exists
            val existing = managedPipelines[name]
            if (existing != null) {
                // Check if graph is the same
                if (existing.graph == request.graph) {
                    success = true
                    jobId = existing.jobId
                    state = Pipeline.PipelineState.UNCHANGED
                    return@applyPipelineResponse
                }

                // Stop the existing job
                try {
                    vm.scheduler.kill(existing.jobId)
                    logger.info { "Stopped existing pipeline job: ${existing.jobId}" }
                } catch (e: Exception) {
                    logger.warn(e) { "Failed to stop existing pipeline: ${existing.jobId}" }
                }
            }

            // Compile and start new job
            val program = graphCompiler.compileFromGraph(request.graph, deterministicId)
            vm.runProgram(program, Session(vm.fileSystem, vm.scheduler, Env(config)))

            managedPipelines[name] = ManagedPipeline(
                metadata = request.metadata,
                graph = request.graph,
                jobId = program.id,
                appliedAt = System.currentTimeMillis()
            )

            success = true
            jobId = program.id
            state = if (existing != null) Pipeline.PipelineState.UPDATED else Pipeline.PipelineState.CREATED
            logger.info { "Applied pipeline '$name' with job ID: ${program.id}" }
        } catch (e: Exception) {
            logger.error(e) { "Failed to apply pipeline" }
            success = false
            error = e.message ?: "Unknown error applying pipeline"
        }
    }

    override suspend fun listPipelines(
        request: Pipeline.ListPipelinesRequest
    ): Pipeline.ListPipelinesResponse = listPipelinesResponse {
        managedPipelines.values.forEach { managed ->
            val jobState = try {
                val job = vm.scheduler.ps().find { it.id == managed.jobId }
                when (job?.state()) {
                    io.typestream.scheduler.Job.State.STARTING -> ProtoJob.JobState.STARTING
                    io.typestream.scheduler.Job.State.RUNNING -> ProtoJob.JobState.RUNNING
                    io.typestream.scheduler.Job.State.STOPPING -> ProtoJob.JobState.STOPPING
                    io.typestream.scheduler.Job.State.STOPPED -> ProtoJob.JobState.STOPPED
                    io.typestream.scheduler.Job.State.FAILED -> ProtoJob.JobState.FAILED
                    io.typestream.scheduler.Job.State.UNKNOWN -> ProtoJob.JobState.UNKNOWN
                    null -> ProtoJob.JobState.STOPPED
                }
            } catch (e: Exception) {
                ProtoJob.JobState.UNKNOWN
            }

            pipelines.add(pipelineInfo {
                this.name = managed.metadata.name
                this.version = managed.metadata.version
                this.description = managed.metadata.description
                this.jobId = managed.jobId
                this.jobState = jobState
                this.appliedAt = managed.appliedAt
                this.graph = managed.graph
            })
        }
    }

    override suspend fun deletePipeline(
        request: Pipeline.DeletePipelineRequest
    ): Pipeline.DeletePipelineResponse = deletePipelineResponse {
        val name = request.name
        val managed = managedPipelines.remove(name)

        if (managed == null) {
            success = false
            error = "Pipeline not found: $name"
            return@deletePipelineResponse
        }

        try {
            vm.scheduler.kill(managed.jobId)
            logger.info { "Stopped and deleted pipeline '$name' (job: ${managed.jobId})" }
        } catch (e: Exception) {
            logger.warn(e) { "Failed to stop pipeline job: ${managed.jobId}" }
        }

        success = true
    }
}
