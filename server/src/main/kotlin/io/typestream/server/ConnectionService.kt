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
 * Represents a monitored database connection with its current state
 */
data class MonitoredConnection(
    val config: Connection.DatabaseConnectionConfig,
    var jdbcConnection: JdbcConnection? = null,
    var state: ConnectionState = ConnectionState.CONNECTION_STATE_UNSPECIFIED,
    var error: String? = null,
    var lastChecked: Instant = Instant.now()
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

    init {
        // Start background health monitoring
        monitorJob = monitorScope.launch {
            while (isActive) {
                delay(healthCheckInterval)
                checkAllConnections()
            }
        }
        logger.info { "ConnectionService started with ${healthCheckInterval.inWholeSeconds}s health check interval" }

        // Register default dev postgres connection for testing
        registerDevPostgresConnection()
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
            .setPort("5432")
            .setDatabase("demo")
            .setUsername("typestream")
            .setPassword("typestream")
            .build()

        logger.info { "Registering default dev-postgres connection (localhost)" }

        try {
            val jdbcConnection = createJdbcConnection(devConfig)
            connections["dev-postgres"] = MonitoredConnection(
                config = devConfig,
                jdbcConnection = jdbcConnection,
                state = ConnectionState.CONNECTED,
                lastChecked = Instant.now()
            )
            logger.info { "dev-postgres connection established successfully" }
        } catch (e: Exception) {
            logger.warn { "dev-postgres connection failed (this is normal if postgres is not running): ${e.message}" }
            connections["dev-postgres"] = MonitoredConnection(
                config = devConfig,
                state = ConnectionState.DISCONNECTED,
                error = e.message,
                lastChecked = Instant.now()
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
                monitored.jdbcConnection?.close()
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
                    jdbcConnection = jdbcConnection,
                    state = ConnectionState.CONNECTED,
                    lastChecked = Instant.now()
                )
                connections[config.id] = monitored

                this.success = true
                this.status = monitored.toProto()
            } catch (e: Exception) {
                logger.warn(e) { "Failed to connect: ${config.name}" }

                // Still register but mark as disconnected
                val monitored = MonitoredConnection(
                    config = config,
                    state = ConnectionState.ERROR,
                    error = e.message,
                    lastChecked = Instant.now()
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
                    monitored.jdbcConnection?.close()
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
     * Check health of a single connection
     */
    private fun checkConnection(monitored: MonitoredConnection) {
        val conn = monitored.jdbcConnection

        try {
            if (conn == null || conn.isClosed) {
                // Try to reconnect
                logger.info { "Reconnecting ${monitored.config.name}" }
                monitored.jdbcConnection = createJdbcConnection(monitored.config)
                monitored.state = ConnectionState.CONNECTED
                monitored.error = null
            } else {
                // Check if connection is still valid (with 5 second timeout)
                if (conn.isValid(5)) {
                    monitored.state = ConnectionState.CONNECTED
                    monitored.error = null
                } else {
                    // Connection is invalid, try to reconnect
                    logger.info { "Connection invalid, reconnecting ${monitored.config.name}" }
                    try {
                        conn.close()
                    } catch (_: Exception) {
                    }
                    monitored.jdbcConnection = createJdbcConnection(monitored.config)
                    monitored.state = ConnectionState.CONNECTED
                    monitored.error = null
                }
            }
        } catch (e: Exception) {
            logger.warn { "Connection ${monitored.config.name} is down: ${e.message}" }
            monitored.state = ConnectionState.DISCONNECTED
            monitored.error = e.message
            monitored.jdbcConnection = null
        }

        monitored.lastChecked = Instant.now()
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
     * Convert MonitoredConnection to proto
     */
    private fun MonitoredConnection.toProto(): Connection.ConnectionStatus = connectionStatus {
        id = this@toProto.config.id
        name = this@toProto.config.name
        state = this@toProto.state
        if (this@toProto.error != null) {
            error = this@toProto.error!!
        }
        lastChecked = Timestamp.newBuilder()
            .setSeconds(this@toProto.lastChecked.epochSecond)
            .setNanos(this@toProto.lastChecked.nano)
            .build()
        config = this@toProto.config  // Include full config for JDBC sink creation
    }
}
