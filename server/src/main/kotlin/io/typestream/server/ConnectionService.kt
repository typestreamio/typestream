package io.typestream.server

import com.google.protobuf.Timestamp
import io.github.oshai.kotlinlogging.KotlinLogging
import io.typestream.grpc.connection_service.Connection
import io.typestream.grpc.connection_service.Connection.ConnectionState
import io.typestream.grpc.connection_service.Connection.DatabaseType
import io.typestream.grpc.connection_service.ConnectionServiceGrpcKt
import io.typestream.grpc.connection_service.connectionStatus
import io.typestream.grpc.connection_service.getConnectionStatusesResponse
import io.typestream.grpc.connection_service.registerConnectionResponse
import io.typestream.grpc.connection_service.testConnectionResponse
import io.typestream.grpc.connection_service.unregisterConnectionResponse
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.Closeable
import java.sql.Connection as JdbcConnection
import java.sql.DriverManager
import java.sql.SQLException
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration.Companion.seconds

/**
 * Immutable snapshot of connection state for thread-safe reads
 */
data class ConnectionStateSnapshot(
    val jdbcConnection: JdbcConnection?,
    val state: ConnectionState,
    val error: String?,
    val lastChecked: Instant
)

/**
 * Represents a monitored database connection with its current state.
 * Uses @Volatile for thread-safe reads from gRPC handlers while
 * being updated by background health check coroutine.
 */
data class MonitoredConnection(
    val config: Connection.DatabaseConnectionConfig,
    @Volatile var stateSnapshot: ConnectionStateSnapshot = ConnectionStateSnapshot(
        jdbcConnection = null,
        state = ConnectionState.CONNECTION_STATE_UNSPECIFIED,
        error = null,
        lastChecked = Instant.now()
    )
)

/**
 * Immutable snapshot of Weaviate connection state for thread-safe reads
 */
data class WeaviateConnectionStateSnapshot(
    val state: ConnectionState,
    val error: String?,
    val lastChecked: Instant
)

/**
 * Represents a monitored Weaviate connection with its current state.
 */
data class MonitoredWeaviateConnection(
    val config: Connection.WeaviateConnectionConfig,
    @Volatile var stateSnapshot: WeaviateConnectionStateSnapshot = WeaviateConnectionStateSnapshot(
        state = ConnectionState.CONNECTION_STATE_UNSPECIFIED,
        error = null,
        lastChecked = Instant.now()
    )
)

/**
 * Service that monitors database connections and reports their status.
 * Maintains persistent JDBC connections to detect connectivity issues.
 */
class ConnectionService : ConnectionServiceGrpcKt.ConnectionServiceCoroutineImplBase(), Closeable {

    private val logger = KotlinLogging.logger {}

    // Map of connection ID to monitored connection
    private val connections = ConcurrentHashMap<String, MonitoredConnection>()

    // Map of Weaviate connection ID to monitored Weaviate connection
    private val weaviateConnections = ConcurrentHashMap<String, MonitoredWeaviateConnection>()

    // Background monitoring coroutine scope
    private val monitorScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var monitorJob: Job? = null

    // How often to check connection health
    private val healthCheckInterval = 10.seconds

    // Whether to register the default dev connections (can be disabled via env vars)
    private val registerDevConnection = System.getenv("TYPESTREAM_REGISTER_DEV_POSTGRES")?.toBoolean() ?: true
    private val registerDevWeaviate = System.getenv("TYPESTREAM_REGISTER_DEV_WEAVIATE")?.toBoolean() ?: true

    init {
        // Start background health monitoring
        monitorJob = monitorScope.launch {
            while (isActive) {
                delay(healthCheckInterval)
                checkAllConnections()
                checkAllWeaviateConnections()
            }
        }
        logger.info { "ConnectionService started with ${healthCheckInterval.inWholeSeconds}s health check interval" }

        // Register default dev postgres connection for testing (can be disabled via TYPESTREAM_REGISTER_DEV_POSTGRES=false)
        if (registerDevConnection) {
            registerDevPostgresConnection()
        } else {
            logger.info { "Skipping dev-postgres registration (TYPESTREAM_REGISTER_DEV_POSTGRES=false)" }
        }

        // Register default dev weaviate connection for testing (can be disabled via TYPESTREAM_REGISTER_DEV_WEAVIATE=false)
        if (registerDevWeaviate) {
            registerDevWeaviateConnection()
        } else {
            logger.info { "Skipping dev-weaviate registration (TYPESTREAM_REGISTER_DEV_WEAVIATE=false)" }
        }
    }

    /**
     * Register the default dev postgres connection on startup
     */
    private fun registerDevPostgresConnection() {
        val devConfig = Connection.DatabaseConnectionConfig.newBuilder()
            .setId("dev-postgres")
            .setName("dev-postgres")
            .setDatabaseType(DatabaseType.POSTGRES)
            .setHostname("localhost")              // For server health checks
            .setConnectorHostname("postgres")     // For Kafka Connect (Docker network)
            .setPort(5432)
            .setDatabase("demo")
            .setUsername("typestream")
            .setPassword("typestream")
            .build()

        logger.info { "Registering default dev-postgres connection (localhost)" }

        try {
            val jdbcConnection = createJdbcConnection(devConfig)
            connections["dev-postgres"] = MonitoredConnection(
                config = devConfig,
                stateSnapshot = ConnectionStateSnapshot(
                    jdbcConnection = jdbcConnection,
                    state = ConnectionState.CONNECTED,
                    error = null,
                    lastChecked = Instant.now()
                )
            )
            logger.info { "dev-postgres connection established successfully" }
        } catch (e: Exception) {
            logger.warn { "dev-postgres connection failed (this is normal if postgres is not running): ${e.message}" }
            connections["dev-postgres"] = MonitoredConnection(
                config = devConfig,
                stateSnapshot = ConnectionStateSnapshot(
                    jdbcConnection = null,
                    state = ConnectionState.DISCONNECTED,
                    error = e.message,
                    lastChecked = Instant.now()
                )
            )
        }
    }

    /**
     * Register the default dev weaviate connection on startup
     */
    private fun registerDevWeaviateConnection() {
        val devConfig = Connection.WeaviateConnectionConfig.newBuilder()
            .setId("dev-weaviate")
            .setName("dev-weaviate")
            .setRestUrl("http://localhost:8090")
            .setGrpcUrl("localhost:50051")
            .setGrpcSecured(false)
            .setAuthScheme("NONE")
            .setConnectorRestUrl("http://weaviate:8080")
            .setConnectorGrpcUrl("weaviate:50051")
            .build()

        logger.info { "Registering default dev-weaviate connection" }

        val isHealthy = checkWeaviateHealth(devConfig.restUrl)
        weaviateConnections["dev-weaviate"] = MonitoredWeaviateConnection(
            config = devConfig,
            stateSnapshot = WeaviateConnectionStateSnapshot(
                state = if (isHealthy) ConnectionState.CONNECTED else ConnectionState.DISCONNECTED,
                error = if (isHealthy) null else "Weaviate not reachable",
                lastChecked = Instant.now()
            )
        )
        if (isHealthy) {
            logger.info { "dev-weaviate connection established successfully" }
        } else {
            logger.warn { "dev-weaviate connection failed (this is normal if weaviate is not running)" }
        }
    }

    override fun close() {
        logger.info { "Shutting down ConnectionService" }
        monitorJob?.cancel()
        monitorScope.cancel()

        // Close all JDBC connections
        connections.values.forEach { monitored ->
            try {
                monitored.stateSnapshot.jdbcConnection?.close()
            } catch (e: Exception) {
                logger.warn(e) { "Error closing connection ${monitored.config.id}" }
            }
        }
        connections.clear()
        weaviateConnections.clear()
    }

    override suspend fun registerConnection(request: Connection.RegisterConnectionRequest): Connection.RegisterConnectionResponse =
        registerConnectionResponse {
            val config = request.connection
            logger.info { "Registering connection: ${config.name} (${config.id})" }

            try {
                // Try to establish connection
                val jdbcConnection = createJdbcConnection(config)
                val monitored = MonitoredConnection(
                    config = config,
                    stateSnapshot = ConnectionStateSnapshot(
                        jdbcConnection = jdbcConnection,
                        state = ConnectionState.CONNECTED,
                        error = null,
                        lastChecked = Instant.now()
                    )
                )
                connections[config.id] = monitored

                this.success = true
                this.status = monitored.toProto()
            } catch (e: Exception) {
                logger.warn(e) { "Failed to connect: ${config.name}" }

                // Still register but mark as disconnected
                val monitored = MonitoredConnection(
                    config = config,
                    stateSnapshot = ConnectionStateSnapshot(
                        jdbcConnection = null,
                        state = ConnectionState.ERROR,
                        error = e.message,
                        lastChecked = Instant.now()
                    )
                )
                connections[config.id] = monitored

                this.success = true // Registration succeeded, but connection failed
                this.status = monitored.toProto()
            }
        }

    override suspend fun unregisterConnection(request: Connection.UnregisterConnectionRequest): Connection.UnregisterConnectionResponse =
        unregisterConnectionResponse {
            val connectionId = request.connectionId
            logger.info { "Unregistering connection: $connectionId" }

            val monitored = connections.remove(connectionId)
            if (monitored != null) {
                try {
                    monitored.stateSnapshot.jdbcConnection?.close()
                } catch (e: Exception) {
                    logger.warn(e) { "Error closing connection $connectionId" }
                }
                this.success = true
            } else {
                this.success = false
                this.error = "Connection not found: $connectionId"
            }
        }

    override suspend fun getConnectionStatuses(request: Connection.GetConnectionStatusesRequest): Connection.GetConnectionStatusesResponse =
        getConnectionStatusesResponse {
            connections.values.forEach { monitored ->
                statuses.add(monitored.toProto())
            }
        }

    override suspend fun testConnection(request: Connection.TestConnectionRequest): Connection.TestConnectionResponse =
        testConnectionResponse {
            val config = request.connection
            logger.info { "Testing connection: ${config.name}" }

            val startTime = System.currentTimeMillis()
            try {
                // Create a temporary connection to test
                val jdbcConnection = createJdbcConnection(config)
                val latency = System.currentTimeMillis() - startTime

                // Run a simple query to verify
                jdbcConnection.use { conn ->
                    conn.createStatement().use { stmt ->
                        stmt.executeQuery("SELECT 1").close()
                    }
                }

                this.success = true
                this.latencyMs = latency
            } catch (e: Exception) {
                logger.warn(e) { "Connection test failed: ${config.name}" }
                this.success = false
                this.error = e.message ?: "Unknown error"
                this.latencyMs = System.currentTimeMillis() - startTime
            }
        }

    /**
     * Check health of all registered connections
     */
    private fun checkAllConnections() {
        connections.values.forEach { monitored ->
            try {
                checkConnection(monitored)
            } catch (e: Exception) {
                logger.warn(e) { "Error checking connection ${monitored.config.id}" }
            }
        }
    }

    /**
     * Check health of a single connection.
     * Updates are done atomically via stateSnapshot replacement.
     */
    private fun checkConnection(monitored: MonitoredConnection) {
        val currentSnapshot = monitored.stateSnapshot
        val conn = currentSnapshot.jdbcConnection

        try {
            if (conn == null || conn.isClosed) {
                // Try to reconnect
                logger.info { "Reconnecting ${monitored.config.name}" }
                val newConn = createJdbcConnection(monitored.config)
                monitored.stateSnapshot = ConnectionStateSnapshot(
                    jdbcConnection = newConn,
                    state = ConnectionState.CONNECTED,
                    error = null,
                    lastChecked = Instant.now()
                )
            } else {
                // Check if connection is still valid (with 5 second timeout)
                if (conn.isValid(5)) {
                    monitored.stateSnapshot = ConnectionStateSnapshot(
                        jdbcConnection = conn,
                        state = ConnectionState.CONNECTED,
                        error = null,
                        lastChecked = Instant.now()
                    )
                } else {
                    // Connection is invalid, try to reconnect
                    logger.info { "Connection invalid, reconnecting ${monitored.config.name}" }
                    try {
                        conn.close()
                    } catch (_: Exception) {
                    }
                    val newConn = createJdbcConnection(monitored.config)
                    monitored.stateSnapshot = ConnectionStateSnapshot(
                        jdbcConnection = newConn,
                        state = ConnectionState.CONNECTED,
                        error = null,
                        lastChecked = Instant.now()
                    )
                }
            }
        } catch (e: Exception) {
            logger.warn { "Connection ${monitored.config.name} is down: ${e.message}" }
            monitored.stateSnapshot = ConnectionStateSnapshot(
                jdbcConnection = null,
                state = ConnectionState.DISCONNECTED,
                error = e.message,
                lastChecked = Instant.now()
            )
        }
    }

    /**
     * Create a JDBC connection from config
     */
    private fun createJdbcConnection(config: Connection.DatabaseConnectionConfig): JdbcConnection {
        val url = buildJdbcUrl(config)
        logger.debug { "Connecting to $url" }

        return DriverManager.getConnection(url, config.username, config.password).also { conn ->
            // Set a reasonable timeout
            try {
                val executor = java.util.concurrent.Executors.newSingleThreadExecutor()
                conn.setNetworkTimeout(executor, 10000) // 10 second timeout
            } catch (e: Exception) {
                logger.debug { "Could not set network timeout: ${e.message}" }
            }
        }
    }

    /**
     * Build JDBC URL from config
     */
    private fun buildJdbcUrl(config: Connection.DatabaseConnectionConfig): String {
        return when (config.databaseType) {
            DatabaseType.POSTGRES -> "jdbc:postgresql://${config.hostname}:${config.port}/${config.database}"
            DatabaseType.MYSQL -> "jdbc:mysql://${config.hostname}:${config.port}/${config.database}"
            else -> throw IllegalArgumentException("Unsupported database type: ${config.databaseType}")
        }
    }

    /**
     * Convert MonitoredConnection to proto (excludes password for security)
     */
    private fun MonitoredConnection.toProto(): Connection.ConnectionStatus = connectionStatus {
        val snapshot = this@toProto.stateSnapshot
        id = this@toProto.config.id
        name = this@toProto.config.name
        state = snapshot.state
        if (snapshot.error != null) {
            error = snapshot.error
        }
        lastChecked = Timestamp.newBuilder()
            .setSeconds(snapshot.lastChecked.epochSecond)
            .setNanos(snapshot.lastChecked.nano)
            .build()
        // Use public config (excludes password)
        config = Connection.DatabaseConnectionConfigPublic.newBuilder()
            .setId(this@toProto.config.id)
            .setName(this@toProto.config.name)
            .setDatabaseType(this@toProto.config.databaseType)
            .setHostname(this@toProto.config.hostname)
            .setPort(this@toProto.config.port)
            .setDatabase(this@toProto.config.database)
            .setUsername(this@toProto.config.username)
            .setConnectorHostname(this@toProto.config.connectorHostname)
            // password intentionally excluded
            .build()
    }

    /**
     * Create a JDBC sink connector using a registered connection.
     * Credentials are resolved server-side from the connection ID.
     */
    override suspend fun createJdbcSinkConnector(request: Connection.CreateJdbcSinkConnectorRequest): Connection.CreateJdbcSinkConnectorResponse {
        val connectionId = request.connectionId
        val monitored = connections[connectionId]

        if (monitored == null) {
            return Connection.CreateJdbcSinkConnectorResponse.newBuilder()
                .setSuccess(false)
                .setError("Connection not found: $connectionId")
                .build()
        }

        val config = monitored.config
        logger.info { "Creating JDBC sink connector: ${request.connectorName} for connection ${config.name}" }

        try {
            // Build JDBC sink connector configuration
            val connectorConfig = buildJdbcSinkConnectorConfig(
                name = request.connectorName,
                config = config,
                topics = request.topics,
                tableName = request.tableName,
                insertMode = request.insertMode,
                primaryKeyFields = request.primaryKeyFields
            )

            // Create connector via Kafka Connect REST API
            createKafkaConnectConnector(connectorConfig)

            return Connection.CreateJdbcSinkConnectorResponse.newBuilder()
                .setSuccess(true)
                .setConnectorName(request.connectorName)
                .build()
        } catch (e: Exception) {
            logger.error(e) { "Failed to create JDBC sink connector: ${request.connectorName}" }
            return Connection.CreateJdbcSinkConnectorResponse.newBuilder()
                .setSuccess(false)
                .setError(e.message ?: "Unknown error")
                .build()
        }
    }

    /**
     * Build the Kafka Connect JDBC sink connector configuration
     */
    private fun buildJdbcSinkConnectorConfig(
        name: String,
        config: Connection.DatabaseConnectionConfig,
        topics: String,
        tableName: String,
        insertMode: String,
        primaryKeyFields: String
    ): Map<String, Any> {
        val hostname = config.connectorHostname.ifEmpty { config.hostname }
        val jdbcUrl = when (config.databaseType) {
            DatabaseType.POSTGRES -> "jdbc:postgresql://$hostname:${config.port}/${config.database}"
            DatabaseType.MYSQL -> "jdbc:mysql://$hostname:${config.port}/${config.database}"
            else -> throw IllegalArgumentException("Unsupported database type: ${config.databaseType}")
        }

        val connectorConfig = mutableMapOf<String, Any>(
            "name" to name,
            "connector.class" to "io.debezium.connector.jdbc.JdbcSinkConnector",
            "tasks.max" to "1",
            "topics" to topics,
            "connection.url" to jdbcUrl,
            "connection.username" to config.username,
            "connection.password" to config.password,
            "table.name.format" to tableName,
            "insert.mode" to insertMode,
            "delete.enabled" to "false",
            "schema.evolution" to "basic",
            // Keys are written as UTF-8 strings by TypeStream
            "key.converter" to "org.apache.kafka.connect.storage.StringConverter",
            "value.converter" to "io.confluent.connect.avro.AvroConverter",
            "value.converter.schema.registry.url" to (System.getenv("SCHEMA_REGISTRY_URL") ?: "http://redpanda:8081"),
            // Unwrap Debezium CDC envelope to get flat record (extracts 'after' payload)
            "transforms" to "unwrap",
            "transforms.unwrap.type" to "io.debezium.transforms.ExtractNewRecordState",
            "transforms.unwrap.drop.tombstones" to "true"
        )

        if (primaryKeyFields.isNotEmpty() && (insertMode == "upsert" || insertMode == "update")) {
            // Use record_value since TypeStream writes keys as strings, not structs
            connectorConfig["primary.key.mode"] = "record_value"
            connectorConfig["primary.key.fields"] = primaryKeyFields
        }

        return mapOf("name" to name, "config" to connectorConfig)
    }

    /**
     * Create a connector via Kafka Connect REST API
     */
    private fun createKafkaConnectConnector(connectorConfig: Map<String, Any>) {
        val connectUrl = System.getenv("KAFKA_CONNECT_URL") ?: "http://localhost:8083"
        val url = java.net.URI.create("$connectUrl/connectors").toURL()
        val connection = url.openConnection() as java.net.HttpURLConnection

        try {
            connection.connectTimeout = 10000
            connection.readTimeout = 30000
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.doOutput = true

            // Simple JSON serialization
            val json = buildJsonString(connectorConfig)
            connection.outputStream.use { os ->
                os.write(json.toByteArray())
            }

            val responseCode = connection.responseCode
            if (responseCode !in 200..299) {
                val errorBody = connection.errorStream?.bufferedReader()?.readText() ?: ""
                throw RuntimeException("Kafka Connect returned $responseCode: $errorBody")
            }

            logger.info { "Successfully created connector: ${connectorConfig["name"]}" }
        } finally {
            connection.disconnect()
        }
    }

    /**
     * Simple JSON builder for connector config
     */
    private fun buildJsonString(map: Map<String, Any>): String {
        val sb = StringBuilder("{")
        var first = true
        for ((key, value) in map) {
            if (!first) sb.append(",")
            first = false
            sb.append("\"$key\":")
            when (value) {
                is String -> sb.append("\"${value.replace("\"", "\\\"")}\"")
                is Map<*, *> -> @Suppress("UNCHECKED_CAST") sb.append(buildJsonString(value as Map<String, Any>))
                else -> sb.append(value)
            }
        }
        sb.append("}")
        return sb.toString()
    }

    // ==================== Weaviate Connection Methods ====================

    /**
     * Register a Weaviate connection for monitoring
     */
    override suspend fun registerWeaviateConnection(request: Connection.RegisterWeaviateConnectionRequest): Connection.RegisterWeaviateConnectionResponse {
        val config = request.connection

        // Validate required fields
        if (config.id.isBlank() || config.name.isBlank() || config.restUrl.isBlank()) {
            return Connection.RegisterWeaviateConnectionResponse.newBuilder()
                .setSuccess(false)
                .setError("Connection id, name, and restUrl are required")
                .build()
        }

        logger.info { "Registering Weaviate connection: ${config.name} (${config.id})" }

        val isHealthy = checkWeaviateHealth(config.restUrl)
        val monitored = MonitoredWeaviateConnection(
            config = config,
            stateSnapshot = WeaviateConnectionStateSnapshot(
                state = if (isHealthy) ConnectionState.CONNECTED else ConnectionState.DISCONNECTED,
                error = if (isHealthy) null else "Weaviate not reachable at ${config.restUrl}",
                lastChecked = Instant.now()
            )
        )
        weaviateConnections[config.id] = monitored

        return Connection.RegisterWeaviateConnectionResponse.newBuilder()
            .setSuccess(true)
            .setStatus(monitored.toWeaviateProto())
            .build()
    }

    /**
     * Get status of all Weaviate connections
     */
    override suspend fun getWeaviateConnectionStatuses(request: Connection.GetWeaviateConnectionStatusesRequest): Connection.GetWeaviateConnectionStatusesResponse {
        val builder = Connection.GetWeaviateConnectionStatusesResponse.newBuilder()
        weaviateConnections.values.forEach { monitored ->
            builder.addStatuses(monitored.toWeaviateProto())
        }
        return builder.build()
    }

    /**
     * Create a Weaviate sink connector using a registered connection.
     * Credentials are resolved server-side from the connection ID.
     */
    override suspend fun createWeaviateSinkConnector(request: Connection.CreateWeaviateSinkConnectorRequest): Connection.CreateWeaviateSinkConnectorResponse {
        val connectionId = request.connectionId
        val monitored = weaviateConnections[connectionId]

        if (monitored == null) {
            return Connection.CreateWeaviateSinkConnectorResponse.newBuilder()
                .setSuccess(false)
                .setError("Weaviate connection not found: $connectionId")
                .build()
        }

        val config = monitored.config
        logger.info { "Creating Weaviate sink connector: ${request.connectorName} for connection ${config.name}" }

        try {
            // Build Weaviate sink connector configuration
            val connectorConfig = buildWeaviateSinkConnectorConfig(
                name = request.connectorName,
                config = config,
                topics = request.topics,
                collectionName = request.collectionName,
                documentIdStrategy = request.documentIdStrategy,
                documentIdField = request.documentIdField,
                vectorStrategy = request.vectorStrategy,
                vectorField = request.vectorField,
                timestampField = request.timestampField
            )

            // Create connector via Kafka Connect REST API
            createKafkaConnectConnector(connectorConfig)

            return Connection.CreateWeaviateSinkConnectorResponse.newBuilder()
                .setSuccess(true)
                .setConnectorName(request.connectorName)
                .build()
        } catch (e: Exception) {
            logger.error(e) { "Failed to create Weaviate sink connector: ${request.connectorName}" }
            return Connection.CreateWeaviateSinkConnectorResponse.newBuilder()
                .setSuccess(false)
                .setError(e.message ?: "Unknown error")
                .build()
        }
    }

    /**
     * Build the Kafka Connect Weaviate sink connector configuration
     */
    private fun buildWeaviateSinkConnectorConfig(
        name: String,
        config: Connection.WeaviateConnectionConfig,
        topics: String,
        collectionName: String,
        documentIdStrategy: String,
        documentIdField: String,
        vectorStrategy: String,
        vectorField: String,
        timestampField: String
    ): Map<String, Any> {
        // Validate required fields
        require(collectionName.isNotBlank()) { "Collection name cannot be empty" }
        if (documentIdStrategy == "FieldIdStrategy") {
            require(documentIdField.isNotBlank()) { "Document ID field required for FieldIdStrategy" }
        }
        if (vectorStrategy == "FieldVectorStrategy") {
            require(vectorField.isNotBlank()) { "Vector field required for FieldVectorStrategy" }
        }

        // Use connector URLs for Kafka Connect (Docker network)
        val weaviateUrl = config.connectorRestUrl.ifEmpty { config.restUrl }
        val grpcHost = config.connectorGrpcUrl.ifEmpty { config.grpcUrl }

        val connectorConfig = mutableMapOf<String, Any>(
            "name" to name,
            "connector.class" to "io.weaviate.connector.WeaviateSinkConnector",
            "tasks.max" to "1",
            "topics" to topics,
            "weaviate.connection.url" to weaviateUrl,
            "weaviate.grpc.url" to grpcHost,
            "weaviate.grpc.secured" to config.grpcSecured.toString(),
            "collection.mapping" to collectionName,
            // Keys are written as UTF-8 strings by TypeStream
            "key.converter" to "org.apache.kafka.connect.storage.StringConverter",
            "value.converter" to "io.confluent.connect.avro.AvroConverter",
            "value.converter.schema.registry.url" to (System.getenv("SCHEMA_REGISTRY_URL") ?: "http://redpanda:8081")
        )

        // Only add timestamp transform if a timestamp field is specified (schema-aware)
        // This converts Avro's timestamp-millis logical type (java.util.Date) to unix epoch (long)
        if (timestampField.isNotBlank()) {
            connectorConfig["transforms"] = "convertTimestamp"
            connectorConfig["transforms.convertTimestamp.type"] = "org.apache.kafka.connect.transforms.TimestampConverter\$Value"
            connectorConfig["transforms.convertTimestamp.target.type"] = "unix"
            connectorConfig["transforms.convertTimestamp.field"] = timestampField
        }

        // Add auth configuration if API key is set
        if (config.authScheme == "API_KEY" && config.apiKey.isNotEmpty()) {
            connectorConfig["weaviate.api.key"] = config.apiKey
        }

        // Document ID strategy configuration
        when (documentIdStrategy) {
            "FieldIdStrategy" -> {
                connectorConfig["document.id.strategy"] = "io.weaviate.connector.idstrategy.FieldIdStrategy"
                connectorConfig["document.id.field.name"] = documentIdField
            }
            "KafkaIdStrategy" -> {
                connectorConfig["document.id.strategy"] = "io.weaviate.connector.idstrategy.KafkaIdStrategy"
            }
            else -> {
                // NoIdStrategy - let Weaviate auto-generate UUIDs
                connectorConfig["document.id.strategy"] = "io.weaviate.connector.idstrategy.NoIdStrategy"
            }
        }

        // Vector strategy configuration (for pre-computed embeddings)
        when (vectorStrategy) {
            "FieldVectorStrategy" -> {
                connectorConfig["vector.strategy"] = "io.weaviate.connector.vectorstrategy.FieldVectorStrategy"
                connectorConfig["vector.field.name"] = vectorField
            }
            else -> {
                // NoVectorStrategy - no pre-computed vectors, Weaviate can vectorize if configured
                connectorConfig["vector.strategy"] = "io.weaviate.connector.vectorstrategy.NoVectorStrategy"
            }
        }

        return mapOf("name" to name, "config" to connectorConfig)
    }

    /**
     * Check health of Weaviate by calling its ready endpoint
     */
    private fun checkWeaviateHealth(restUrl: String): Boolean {
        return try {
            val url = java.net.URI.create("$restUrl/v1/.well-known/ready").toURL()
            val connection = url.openConnection() as java.net.HttpURLConnection
            connection.connectTimeout = 5000
            connection.readTimeout = 5000
            connection.requestMethod = "GET"

            try {
                val responseCode = connection.responseCode
                responseCode == 200
            } finally {
                connection.disconnect()
            }
        } catch (e: Exception) {
            logger.debug { "Weaviate health check failed for $restUrl: ${e.message}" }
            false
        }
    }

    /**
     * Check health of all registered Weaviate connections
     */
    private fun checkAllWeaviateConnections() {
        weaviateConnections.values.forEach { monitored ->
            try {
                val isHealthy = checkWeaviateHealth(monitored.config.restUrl)
                monitored.stateSnapshot = WeaviateConnectionStateSnapshot(
                    state = if (isHealthy) ConnectionState.CONNECTED else ConnectionState.DISCONNECTED,
                    error = if (isHealthy) null else "Weaviate not reachable",
                    lastChecked = Instant.now()
                )
            } catch (e: Exception) {
                logger.warn(e) { "Error checking Weaviate connection ${monitored.config.id}" }
                monitored.stateSnapshot = WeaviateConnectionStateSnapshot(
                    state = ConnectionState.ERROR,
                    error = e.message ?: "Unknown error during health check",
                    lastChecked = Instant.now()
                )
            }
        }
    }

    /**
     * Convert MonitoredWeaviateConnection to proto (excludes api_key for security)
     */
    private fun MonitoredWeaviateConnection.toWeaviateProto(): Connection.WeaviateConnectionStatus {
        val snapshot = this.stateSnapshot
        return Connection.WeaviateConnectionStatus.newBuilder()
            .setId(this.config.id)
            .setName(this.config.name)
            .setState(snapshot.state)
            .setError(snapshot.error ?: "")
            .setLastChecked(
                Timestamp.newBuilder()
                    .setSeconds(snapshot.lastChecked.epochSecond)
                    .setNanos(snapshot.lastChecked.nano)
                    .build()
            )
            .setConfig(
                Connection.WeaviateConnectionConfigPublic.newBuilder()
                    .setId(this.config.id)
                    .setName(this.config.name)
                    .setRestUrl(this.config.restUrl)
                    .setGrpcUrl(this.config.grpcUrl)
                    .setGrpcSecured(this.config.grpcSecured)
                    .setAuthScheme(this.config.authScheme)
                    // api_key intentionally excluded
                    .setConnectorRestUrl(this.config.connectorRestUrl)
                    .setConnectorGrpcUrl(this.config.connectorGrpcUrl)
                    .build()
            )
            .build()
    }

    /**
     * Get a Weaviate connection config by ID (used by JobService)
     */
    fun getWeaviateConnection(connectionId: String): MonitoredWeaviateConnection? {
        return weaviateConnections[connectionId]
    }
}
