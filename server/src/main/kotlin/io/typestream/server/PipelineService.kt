package io.typestream.server

import io.github.oshai.kotlinlogging.KotlinLogging
import io.typestream.compiler.DesugarResult
import io.typestream.compiler.GraphCompiler
import io.typestream.compiler.PipelineDesugarer
import io.typestream.compiler.vm.Env
import io.typestream.grpc.connection_service.Connection
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
import io.typestream.grpc.pipeline_service.pipelinePlanResult
import io.typestream.grpc.pipeline_service.planPipelinesResponse
import io.typestream.grpc.pipeline_service.validatePipelineResponse
import io.typestream.pipeline.PipelineRecord
import io.typestream.pipeline.PipelineStateStore
import java.io.Closeable
import java.util.concurrent.ConcurrentHashMap

data class ManagedPipeline(
    val metadata: Pipeline.PipelineMetadata,
    val userGraph: ProtoJob.UserPipelineGraph,
    val graph: ProtoJob.PipelineGraph,
    val jobId: String,
    val appliedAt: Long = System.currentTimeMillis()
)

class PipelineService(
    private val config: Config,
    private val vm: Vm,
    private val stateStore: PipelineStateStore? = null,
    private val connectionService: ConnectionService? = null
) : PipelineServiceGrpcKt.PipelineServiceCoroutineImplBase(), Closeable {

    private val logger = KotlinLogging.logger {}
    private val graphCompiler = GraphCompiler(vm.fileSystem)
    private val managedPipelines = ConcurrentHashMap<String, ManagedPipeline>()

    init {
        if (stateStore != null) {
            try {
                stateStore.ensureTopicExists()
                val loaded = stateStore.load()
                loaded.forEach { (name, record) ->
                    val deterministicId = "typestream-pipeline-$name"
                    managedPipelines[name] = ManagedPipeline(
                        metadata = record.metadata,
                        userGraph = record.userGraph,
                        graph = record.graph,
                        jobId = deterministicId,
                        appliedAt = record.appliedAt
                    )
                }
                logger.info { "Loaded ${loaded.size} pipeline(s) from state store" }
            } catch (e: Exception) {
                logger.warn(e) { "Failed to load pipelines from state store (Kafka may be unavailable)" }
            }
        }
    }

    suspend fun recoverPipelines() {
        val pipelines = managedPipelines.values.toList()
        if (pipelines.isEmpty()) return
        logger.info { "Recovering ${pipelines.size} managed pipeline(s)..." }
        for (managed in pipelines) {
            try {
                val deterministicId = "typestream-pipeline-${managed.metadata.name}"
                val program = graphCompiler.compileFromGraph(managed.graph, deterministicId)
                vm.runProgram(program, Session(vm.fileSystem, vm.scheduler, Env(config)))
                logger.info { "Recovered pipeline '${managed.metadata.name}' (job: ${program.id})" }
            } catch (e: Exception) {
                logger.error(e) { "Failed to recover pipeline '${managed.metadata.name}'" }
            }
        }
    }

    /**
     * Create the Kafka Connect sink connectors derived by the desugarer for this pipeline.
     *
     * Connector names are deterministic ("<jobId>-qdrant-sink-<nodeId>"), so re-applying a
     * changed pipeline replaces the existing connector (delete-then-create) — pointing it at
     * the freshly-generated intermediate topic. Connectors persist in Kafka Connect across
     * server restarts, so [recoverPipelines] intentionally does not recreate them.
     */
    private suspend fun createSinkConnectors(jobId: String, desugared: DesugarResult) {
        val cs = connectionService ?: return
        for (config in desugared.qdrantSinkConfigs) {
            val connectorName = "$jobId-qdrant-sink-${config.nodeId}"
            deleteKafkaConnectConnector(connectorName) // idempotent replace on re-apply
            val req = Connection.CreateQdrantSinkConnectorRequest.newBuilder()
                .setConnectionId(config.connectionId)
                .setConnectorName(connectorName)
                .setTopics(config.intermediateTopic)
                .setCollectionName(config.collectionName)
                .build()
            val resp = cs.createQdrantSinkConnector(req)
            if (!resp.success) {
                throw RuntimeException("Failed to create Qdrant connector $connectorName: ${resp.error}")
            }
            logger.info { "Created Qdrant sink connector: $connectorName" }
        }
    }

    private fun connectorNamesFor(jobId: String, userGraph: ProtoJob.UserPipelineGraph): List<String> =
        PipelineDesugarer.desugar(userGraph).qdrantSinkConfigs.map { "$jobId-qdrant-sink-${it.nodeId}" }

    private fun deleteKafkaConnectConnector(connectorName: String) {
        try {
            val connectUrl = System.getenv("KAFKA_CONNECT_URL") ?: "http://localhost:8083"
            val url = java.net.URI.create("$connectUrl/connectors/$connectorName").toURL()
            val connection = url.openConnection() as java.net.HttpURLConnection
            try {
                connection.connectTimeout = 10000
                connection.readTimeout = 30000
                connection.requestMethod = "DELETE"
                connection.responseCode // trigger the request; 404 is fine (nothing to delete)
            } finally {
                connection.disconnect()
            }
        } catch (e: Exception) {
            logger.debug { "Delete connector $connectorName: ${e.message}" }
        }
    }

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

            // Desugar then dry-run compile to check for errors
            val desugared = PipelineDesugarer.desugar(request.graph)
            graphCompiler.compileFromGraph(desugared.graph, "typestream-pipeline-$name")

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

            // Check if pipeline already exists — compare user graphs
            val existing = managedPipelines[name]
            if (existing != null) {
                if (existing.userGraph == request.graph) {
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

            // Desugar, compile, and start new job
            val desugared = PipelineDesugarer.desugar(request.graph)
            val program = graphCompiler.compileFromGraph(desugared.graph, deterministicId)
            vm.runProgram(program, Session(vm.fileSystem, vm.scheduler, Env(config)))

            // Provision sink connectors for this pipeline. Unlike the UI's CreateJobFromGraph
            // path, pipeline-as-code (ApplyPipeline) is responsible for creating the Kafka
            // Connect sinks the desugarer derived. Roll the job back if a connector fails.
            try {
                createSinkConnectors(deterministicId, desugared)
            } catch (e: Exception) {
                logger.error(e) { "Connector creation failed, rolling back pipeline '$name'" }
                try {
                    vm.scheduler.kill(program.id)
                } catch (killErr: Exception) {
                    logger.warn(killErr) { "Failed to kill job ${program.id} during rollback" }
                }
                success = false
                error = "Connector creation failed: ${e.message}"
                return@applyPipelineResponse
            }

            val now = System.currentTimeMillis()
            managedPipelines[name] = ManagedPipeline(
                metadata = request.metadata,
                userGraph = request.graph,
                graph = desugared.graph,
                jobId = program.id,
                appliedAt = now
            )

            // Persist to state store
            stateStore?.save(name, PipelineRecord(
                metadata = request.metadata,
                userGraph = request.graph,
                graph = desugared.graph,
                appliedAt = now
            ))

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
                this.userGraph = managed.userGraph
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

        // Remove the sink connectors this pipeline provisioned.
        if (connectionService != null) {
            connectorNamesFor(managed.jobId, managed.userGraph).forEach { deleteKafkaConnectConnector(it) }
        }

        // Remove from state store
        stateStore?.delete(name)

        success = true
    }

    override suspend fun planPipelines(
        request: Pipeline.PlanPipelinesRequest
    ): Pipeline.PlanPipelinesResponse = planPipelinesResponse {
        val requestedNames = mutableSetOf<String>()
        val currentPipelines = managedPipelines.toMap()

        for (plan in request.pipelinesList) {
            val name = plan.metadata.name
            if (name.isBlank()) {
                errors.add("Pipeline name is required")
                continue
            }
            requestedNames.add(name)

            // Desugar and validate the graph compiles
            val deterministicId = "typestream-pipeline-$name"
            val validationError = try {
                val desugared = PipelineDesugarer.desugar(plan.graph)
                graphCompiler.compileFromGraph(desugared.graph, deterministicId)
                null
            } catch (e: Exception) {
                e.message ?: "Unknown validation error"
            }

            val existing = currentPipelines[name]
            results.add(pipelinePlanResult {
                this.name = name
                when {
                    existing == null -> {
                        action = Pipeline.PipelineAction.CREATE
                        newVersion = plan.metadata.version
                    }
                    existing.userGraph == plan.graph -> {
                        action = Pipeline.PipelineAction.NO_CHANGE
                        currentVersion = existing.metadata.version
                        newVersion = plan.metadata.version
                    }
                    else -> {
                        action = Pipeline.PipelineAction.UPDATE
                        currentVersion = existing.metadata.version
                        newVersion = plan.metadata.version
                    }
                }
            })

            if (validationError != null) {
                errors.add("Pipeline '$name': $validationError")
            }
        }

        // Pipelines that exist but are not in the request → DELETE
        for ((name, managed) in currentPipelines) {
            if (name !in requestedNames) {
                results.add(pipelinePlanResult {
                    this.name = name
                    action = Pipeline.PipelineAction.DELETE
                    currentVersion = managed.metadata.version
                })
            }
        }
    }

    override fun close() {
        stateStore?.close()
    }
}
