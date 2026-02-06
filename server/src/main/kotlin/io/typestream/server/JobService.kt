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
import io.typestream.grpc.job_service.Job.CreatePreviewJobRequest
import io.typestream.grpc.job_service.Job.StopPreviewJobRequest
import io.typestream.grpc.job_service.Job.StreamPreviewRequest
import io.typestream.grpc.job_service.JobServiceGrpcKt
import io.typestream.grpc.job_service.createJobResponse
import io.typestream.grpc.job_service.createPreviewJobResponse
import io.typestream.grpc.job_service.stopPreviewJobResponse
import io.typestream.grpc.job_service.streamPreviewResponse
import io.typestream.grpc.job_service.listJobsResponse
import io.typestream.grpc.job_service.jobInfo
import io.typestream.grpc.job_service.jobThroughput
import io.typestream.grpc.job_service.inferGraphSchemasResponse
import io.typestream.grpc.job_service.nodeSchemaResult
import io.typestream.grpc.job_service.schemaField
import io.typestream.grpc.job_service.listOpenAIModelsResponse
import io.typestream.grpc.job_service.openAIModel
import io.typestream.openai.OpenAiService
import io.typestream.compiler.types.schema.Schema
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

class JobService(
    private val config: Config,
    private val vm: Vm,
    private val connectionService: ConnectionService
) : JobServiceGrpcKt.JobServiceCoroutineImplBase(), Closeable {

    private val logger = KotlinLogging.logger {}
    private val graphCompiler = GraphCompiler(vm.fileSystem)
    private val openAiService = OpenAiService()

    // Track preview jobs for cleanup: jobId -> PreviewJobInfo
    private val previewJobs = ConcurrentHashMap<String, PreviewJobInfo>()

    // Track weaviate sink configs per job: jobId -> List<WeaviateSinkConfig>
    private val jobWeaviateSinks = ConcurrentHashMap<String, List<ProtoJob.WeaviateSinkConfig>>()

    // Track elasticsearch sink configs per job: jobId -> List<ElasticsearchSinkConfig>
    private val jobElasticsearchSinks = ConcurrentHashMap<String, List<ProtoJob.ElasticsearchSinkConfig>>()

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

            // Pass program to scheduler (same path as text compiler)
            if (config.k8sMode) {
                // TODO: Phase 4 - Serialize program to JSON for K8s worker
                // For now, K8s mode is not supported for graph-based jobs
                this.success = false
                this.error = "K8s mode not yet supported for graph-based jobs (Phase 4)"
                return@createJobResponse
            }

            vm.runProgram(program, Session(vm.fileSystem, vm.scheduler, Env(config)))

            // Create sink connectors if any configs are provided
            val createdConnectorNames = mutableListOf<String>()
            val dbSinkConfigs = request.dbSinkConfigsList
            val weaviateSinkConfigs = request.weaviateSinkConfigsList

            // Create JDBC sink connectors
            if (dbSinkConfigs.isNotEmpty()) {
                logger.info { "Creating ${dbSinkConfigs.size} JDBC sink connector(s) for job ${program.id}" }

                for (config in dbSinkConfigs) {
                    val connectorName = "${program.id}-jdbc-sink-${config.nodeId}"
                    val intermediateTopic = config.intermediateTopic.ifEmpty {
                        generateIntermediateTopicName(config.nodeId)
                    }

                    try {
                        val connectorRequest = io.typestream.grpc.connection_service.Connection.CreateJdbcSinkConnectorRequest.newBuilder()
                            .setConnectionId(config.connectionId)
                            .setConnectorName(connectorName)
                            .setTopics(intermediateTopic)
                            .setTableName(config.tableName)
                            .setInsertMode(config.insertMode)
                            .setPrimaryKeyFields(config.primaryKeyFields)
                            .build()

                        val response = connectionService.createJdbcSinkConnector(connectorRequest)

                        if (!response.success) {
                            throw RuntimeException("Failed to create connector $connectorName: ${response.error}")
                        }

                        createdConnectorNames.add(connectorName)
                        logger.info { "Created JDBC sink connector: $connectorName" }
                    } catch (e: Exception) {
                        // Rollback: Kill the job and delete any created connectors
                        logger.error(e) { "Connector creation failed, rolling back job ${program.id}" }
                        rollbackJobAndConnectors(program.id, createdConnectorNames)

                        this.success = false
                        this.jobId = ""
                        this.error = "Connector creation failed: ${e.message}"
                        return@createJobResponse
                    }
                }
            }

            // Create Weaviate sink connectors
            if (weaviateSinkConfigs.isNotEmpty()) {
                logger.info { "Creating ${weaviateSinkConfigs.size} Weaviate sink connector(s) for job ${program.id}" }

                for (config in weaviateSinkConfigs) {
                    val connectorName = "${program.id}-weaviate-sink-${config.nodeId}"
                    val intermediateTopic = config.intermediateTopic.ifEmpty {
                        generateWeaviateIntermediateTopicName(config.nodeId)
                    }

                    try {
                        val connectorRequest = io.typestream.grpc.connection_service.Connection.CreateWeaviateSinkConnectorRequest.newBuilder()
                            .setConnectionId(config.connectionId)
                            .setConnectorName(connectorName)
                            .setTopics(intermediateTopic)
                            .setCollectionName(config.collectionName)
                            .setDocumentIdStrategy(config.documentIdStrategy)
                            .setDocumentIdField(config.documentIdField)
                            .setVectorStrategy(config.vectorStrategy)
                            .setVectorField(config.vectorField)
                            .setTimestampField(config.timestampField)
                            .build()

                        val response = connectionService.createWeaviateSinkConnector(connectorRequest)

                        if (!response.success) {
                            throw RuntimeException("Failed to create Weaviate connector $connectorName: ${response.error}")
                        }

                        createdConnectorNames.add(connectorName)
                        logger.info { "Created Weaviate sink connector: $connectorName" }
                    } catch (e: Exception) {
                        // Rollback: Kill the job and delete any created connectors
                        logger.error(e) { "Weaviate connector creation failed, rolling back job ${program.id}" }
                        rollbackJobAndConnectors(program.id, createdConnectorNames)

                        this.success = false
                        this.jobId = ""
                        this.error = "Weaviate connector creation failed: ${e.message}"
                        return@createJobResponse
                    }
                }
            }

            // Create Elasticsearch sink connectors
            val elasticsearchSinkConfigs = request.elasticsearchSinkConfigsList
            if (elasticsearchSinkConfigs.isNotEmpty()) {
                logger.info { "Creating ${elasticsearchSinkConfigs.size} Elasticsearch sink connector(s) for job ${program.id}" }

                for (config in elasticsearchSinkConfigs) {
                    val connectorName = "${program.id}-elasticsearch-sink-${config.nodeId}"
                    val intermediateTopic = config.intermediateTopic.ifEmpty {
                        generateElasticsearchIntermediateTopicName(config.nodeId)
                    }

                    try {
                        val connectorRequest = io.typestream.grpc.connection_service.Connection.CreateElasticsearchSinkConnectorRequest.newBuilder()
                            .setConnectionId(config.connectionId)
                            .setConnectorName(connectorName)
                            .setTopics(intermediateTopic)
                            .setIndexName(config.indexName)
                            .setDocumentIdStrategy(config.documentIdStrategy)
                            .setWriteMethod(config.writeMethod)
                            .setBehaviorOnNullValues(config.behaviorOnNullValues)
                            .build()

                        val response = connectionService.createElasticsearchSinkConnector(connectorRequest)

                        if (!response.success) {
                            throw RuntimeException("Failed to create Elasticsearch connector $connectorName: ${response.error}")
                        }

                        createdConnectorNames.add(connectorName)
                        logger.info { "Created Elasticsearch sink connector: $connectorName" }
                    } catch (e: Exception) {
                        // Rollback: Kill the job and delete any created connectors
                        logger.error(e) { "Elasticsearch connector creation failed, rolling back job ${program.id}" }
                        rollbackJobAndConnectors(program.id, createdConnectorNames)

                        this.success = false
                        this.jobId = ""
                        this.error = "Elasticsearch connector creation failed: ${e.message}"
                        return@createJobResponse
                    }
                }
            }

            // Store weaviate sink configs for this job (for job details display)
            if (weaviateSinkConfigs.isNotEmpty()) {
                jobWeaviateSinks[program.id] = weaviateSinkConfigs
            }

            // Store elasticsearch sink configs for this job (for job details display)
            if (elasticsearchSinkConfigs.isNotEmpty()) {
                jobElasticsearchSinks[program.id] = elasticsearchSinkConfigs
            }

            this.success = true
            this.jobId = program.id
            this.error = ""
            this.createdConnectors.addAll(createdConnectorNames)
        } catch (e: Exception) {
            this.success = false
            this.jobId = ""
            this.error = e.message ?: "Unknown error during graph compilation"
        }
    }

    /**
     * Generate a unique topic name for intermediate JDBC sink output
     */
    private fun generateIntermediateTopicName(nodeId: String): String {
        val timestamp = System.currentTimeMillis()
        val sanitizedNodeId = nodeId.replace(Regex("[^a-zA-Z0-9]"), "-")
        return "jdbc-sink-$sanitizedNodeId-$timestamp"
    }

    /**
     * Generate a unique topic name for intermediate Weaviate sink output
     */
    private fun generateWeaviateIntermediateTopicName(nodeId: String): String {
        val timestamp = System.currentTimeMillis()
        val sanitizedNodeId = nodeId.replace(Regex("[^a-zA-Z0-9]"), "-")
        return "weaviate-sink-$sanitizedNodeId-$timestamp"
    }

    /**
     * Generate a unique topic name for intermediate Elasticsearch sink output
     */
    private fun generateElasticsearchIntermediateTopicName(nodeId: String): String {
        val timestamp = System.currentTimeMillis()
        val sanitizedNodeId = nodeId.replace(Regex("[^a-zA-Z0-9]"), "-")
        return "elasticsearch-sink-$sanitizedNodeId-$timestamp"
    }

    /**
     * Rollback a failed job creation by killing the job and deleting connectors
     */
    private fun rollbackJobAndConnectors(jobId: String, connectorNames: List<String>) {
        // Kill the job
        try {
            vm.scheduler.kill(jobId)
            logger.info { "Killed job $jobId during rollback" }
        } catch (e: Exception) {
            logger.warn(e) { "Failed to kill job $jobId during rollback" }
        }

        // Delete any created connectors
        connectorNames.forEach { connectorName ->
            try {
                deleteKafkaConnectConnector(connectorName)
                logger.info { "Deleted connector $connectorName during rollback" }
            } catch (e: Exception) {
                logger.warn(e) { "Failed to delete connector $connectorName during rollback" }
            }
        }
    }

    /**
     * Delete a connector via Kafka Connect REST API
     */
    private fun deleteKafkaConnectConnector(connectorName: String) {
        val connectUrl = System.getenv("KAFKA_CONNECT_URL") ?: "http://localhost:8083"
        val url = java.net.URI.create("$connectUrl/connectors/$connectorName").toURL()
        val connection = url.openConnection() as java.net.HttpURLConnection

        try {
            connection.requestMethod = "DELETE"
            val responseCode = connection.responseCode
            if (responseCode !in 200..299 && responseCode != 404) {
                val errorBody = connection.errorStream?.bufferedReader()?.readText() ?: ""
                throw RuntimeException("Kafka Connect returned $responseCode: $errorBody")
            }
        } finally {
            connection.disconnect()
        }
    }

    override suspend fun listJobs(request: ListJobsRequest): ProtoJob.ListJobsResponse = listJobsResponse {
        try {
            // Get list of running jobs from scheduler
            val runningJobs = vm.scheduler.ps()

            runningJobs.forEach { schedulerJob ->
                // Skip preview jobs from the listing
                if (previewJobs.containsKey(schedulerJob.id)) return@forEach

                val jobThroughputMetrics = schedulerJob.throughput()
                jobs.add(jobInfo {
                    jobId = schedulerJob.id
                    state = when (schedulerJob.state()) {
                        SchedulerJob.State.STARTING -> ProtoJob.JobState.STARTING
                        SchedulerJob.State.RUNNING -> ProtoJob.JobState.RUNNING
                        SchedulerJob.State.STOPPING -> ProtoJob.JobState.STOPPING
                        SchedulerJob.State.STOPPED -> ProtoJob.JobState.STOPPED
                        SchedulerJob.State.FAILED -> ProtoJob.JobState.FAILED
                        SchedulerJob.State.UNKNOWN -> ProtoJob.JobState.UNKNOWN
                    }
                    startTime = schedulerJob.startTime
                    // Include graph if available (only for graph-based jobs)
                    if (schedulerJob is io.typestream.scheduler.KafkaStreamsJob) {
                        schedulerJob.program.pipelineGraph?.let { graph = it }
                    }
                    throughput = jobThroughput {
                        messagesPerSecond = jobThroughputMetrics.messagesPerSecond
                        totalMessages = jobThroughputMetrics.totalMessages
                        bytesPerSecond = jobThroughputMetrics.bytesPerSecond
                        totalBytes = jobThroughputMetrics.totalBytes
                    }
                    // Include weaviate sink configs if available
                    jobWeaviateSinks[schedulerJob.id]?.let { configs ->
                        weaviateSinks.addAll(configs)
                    }
                    // Include elasticsearch sink configs if available
                    jobElasticsearchSinks[schedulerJob.id]?.let { configs ->
                        elasticsearchSinks.addAll(configs)
                    }
                })
            }
        } catch (e: Exception) {
            logger.error(e) { "Error listing jobs" }
            // Return empty list on error
        }
    }

    /**
     * Creates a preview job: compiles the graph into a real Kafka Streams job,
     * starts it, and returns the inspect topic name. Inspector nodes in the graph
     * branch data to side-channel topics (see [KafkaStreamSource.toInspector]).
     * Use [streamPreview] to consume messages from the inspect topic.
     */
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

    /**
     * Streams preview messages from the inspect topic to the client via gRPC server streaming.
     * Delegates to [KafkaStreamsJob.outputWithKey] which sets up a real KafkaConsumer.
     * Auto-cleans up the preview job when the stream ends (disconnect, error, or completion).
     */
    override fun streamPreview(request: StreamPreviewRequest): Flow<ProtoJob.StreamPreviewResponse> = flow {
        val jobId = request.jobId
        val info = previewJobs[jobId] ?: error("Preview job not found: $jobId")
        val inspectTopic = "$jobId-inspect-${info.inspectorNodeId}"

        try {
            // Use cancellable() to ensure the flow responds to cancellation signals
            vm.scheduler.jobOutputWithKey(jobId, inspectTopic).cancellable().collect { record ->
                // Check for cancellation before each emit (ensures we respond to client disconnect)
                currentCoroutineContext().ensureActive()
                emit(streamPreviewResponse {
                    this.key = record.key
                    this.value = record.value
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

    override suspend fun inferGraphSchemas(request: ProtoJob.InferGraphSchemasRequest): ProtoJob.InferGraphSchemasResponse = inferGraphSchemasResponse {
        // Use UI-friendly inference that handles errors gracefully per-node
        val results = graphCompiler.inferNodeSchemasForUI(request.graph)

        request.graph.nodesList.forEach { node ->
            val result = results[node.id]
            schemas[node.id] = nodeSchemaResult {
                val schema = result?.schema?.schema
                if (schema is Schema.Struct) {
                    // For CDC envelope schemas, extract fields from 'after' payload
                    val unwrappedStruct = unwrapCdcEnvelopeStruct(schema)
                    // Populate both fields (backward compat) and typed_fields (new)
                    unwrappedStruct.value.forEach { field ->
                        fields += field.name
                        typedFields += schemaField {
                            name = field.name
                            type = field.value.printTypes()
                        }
                    }
                }
                encoding = result?.encoding?.name ?: "AVRO"
                if (result?.error != null) {
                    error = result.error
                }
            }
        }
    }

    /**
     * Detects CDC envelope schemas and extracts the 'after' struct.
     * CDC envelopes have: before, after, source, op, ts_ms (and optionally ts_us, ts_ns, transaction)
     * Returns the inner struct so we can access both field names and types.
     */
    private fun unwrapCdcEnvelopeStruct(struct: Schema.Struct): Schema.Struct {
        val fieldNames = struct.value.map { it.name }.toSet()
        val isCdcEnvelope = fieldNames.containsAll(setOf("before", "after", "source", "op"))

        if (isCdcEnvelope) {
            // Find the 'after' field and extract its struct
            val afterField = struct.value.find { it.name == "after" }
            val afterValue = afterField?.value

            // Handle both direct Struct and Optional<Struct> (nullable in Avro)
            val afterStruct = when (afterValue) {
                is Schema.Struct -> afterValue
                is Schema.Optional -> afterValue.value as? Schema.Struct
                else -> null
            }

            if (afterStruct != null) {
                return afterStruct
            }
        }

        // Not a CDC envelope, return original struct
        return struct
    }

    override suspend fun listOpenAIModels(request: ProtoJob.ListOpenAIModelsRequest): ProtoJob.ListOpenAIModelsResponse = listOpenAIModelsResponse {
        val fetchedModels = openAiService.fetchModels()
        fetchedModels.forEach { m ->
            models.add(openAIModel {
                id = m.id
                name = m.name
            })
        }
    }
}
