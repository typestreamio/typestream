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
 * Service that monitors database connections and reports their status.
 * Maintains persistent JDBC connections to detect connectivity issues.
 */
class ConnectionService : ConnectionServiceGrpcKt.ConnectionServiceCoroutineImplBase(), Closeable {

    private val logger = KotlinLogging.logger {}

    // Map of connection ID to monitored connection
    private val connections = ConcurrentHashMap<String, MonitoredConnection>()

    // Background monitoring coroutine scope
    private val monitorScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var monitorJob: Job? = null

    // How often to check connection health
    private val healthCheckInterval = 10.seconds

    // Whether to register the default dev-postgres connection (can be disabled via env var)
    private val registerDevConnection = System.getenv("TYPESTREAM_REGISTER_DEV_POSTGRES")?.toBoolean() ?: true

    init {
        // Start background health monitoring
        monitorJob = monitorScope.launch {
            while (isActive) {
                delay(healthCheckInterval)
                checkAllConnections()
            }
        }
        logger.info { "ConnectionService started with ${healthCheckInterval.inWholeSeconds}s health check interval" }

        // Register default dev postgres connection for testing (can be disabled via TYPESTREAM_REGISTER_DEV_POSTGRES=false)
        if (registerDevConnection) {
            registerDevPostgresConnection()
        } else {
            logger.info { "Skipping dev-postgres registration (TYPESTREAM_REGISTER_DEV_POSTGRES=false)" }
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
        val url = java.net.URL("$connectUrl/connectors")
        val connection = url.openConnection() as java.net.HttpURLConnection

        try {
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
                is Map<*, *> -> sb.append(buildJsonString(value as Map<String, Any>))
                else -> sb.append(value)
            }
        }
        sb.append("}")
        return sb.toString()
    }
}
