package io.typestream.filesystem.kafka

import io.github.oshai.kotlinlogging.KotlinLogging
import io.typestream.config.KafkaConfig
import io.typestream.coroutine.tick
import io.typestream.filesystem.Directory
import io.typestream.kafka.KafkaAdminClient
import io.typestream.kafka.schemaregistry.SchemaRegistryClient
import kotlinx.coroutines.supervisorScope
import org.apache.kafka.clients.admin.Admin
import org.apache.kafka.clients.admin.AdminClientConfig
import org.apache.kafka.common.config.SaslConfigs
import java.util.Properties
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.seconds


class KafkaClusterDirectory(
    name: String,
    private val kafkaConfig: KafkaConfig,
) : Directory(name) {
    private val logger = KotlinLogging.logger {}
    private val brokersDir = Directory("brokers")
    private val consumerGroupsDir = Directory("consumer-groups")
    private val topicsDir = Directory("topics")
    private val schemaRegistryDir = Directory("schemas")

    private val kafkaAdminClient = KafkaAdminClient(kafkaConfig)

    private val schemaRegistryClient = SchemaRegistryClient(kafkaConfig.schemaRegistry)

    init {
        setOf(brokersDir, consumerGroupsDir, topicsDir, schemaRegistryDir).forEach(::add)
    }

    fun verifyConnectivity(timeoutMs: Long = 10_000) {
        verifyKafkaConnectivity(timeoutMs)
        verifySchemaRegistryConnectivity()
    }

    private fun verifyKafkaConnectivity(timeoutMs: Long) {
        logger.info { "verifying Kafka connectivity for cluster '$name' at ${kafkaConfig.bootstrapServers}" }
        val props = Properties().apply {
            put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaConfig.bootstrapServers)
            put(AdminClientConfig.REQUEST_TIMEOUT_MS_CONFIG, timeoutMs.toInt())
            put(AdminClientConfig.DEFAULT_API_TIMEOUT_MS_CONFIG, timeoutMs.toInt())
        }
        kafkaConfig.saslConfig?.let {
            props[AdminClientConfig.SECURITY_PROTOCOL_CONFIG] = "SASL_SSL"
            props[SaslConfigs.SASL_MECHANISM] = it.mechanism
            props[SaslConfigs.SASL_JAAS_CONFIG] = it.jaasConfig
        }
        try {
            Admin.create(props).use { admin ->
                admin.describeCluster().clusterId().get(timeoutMs, TimeUnit.MILLISECONDS)
            }
            logger.info { "Kafka cluster '$name' is reachable" }
        } catch (e: Exception) {
            throw IllegalStateException(
                "cannot start: Kafka cluster '$name' is unreachable at ${kafkaConfig.bootstrapServers}", e
            )
        }
    }

    private fun verifySchemaRegistryConnectivity() {
        logger.info { "verifying Schema Registry connectivity for cluster '$name' at ${kafkaConfig.schemaRegistry.url}" }
        try {
            schemaRegistryClient.subjects()
            logger.info { "Schema Registry for cluster '$name' is reachable" }
        } catch (e: Exception) {
            throw IllegalStateException(
                "cannot start: Schema Registry for cluster '$name' is unreachable at ${kafkaConfig.schemaRegistry.url}", e
            )
        }
    }

    override fun stat() = buildString {
        appendLine("File: $name")

        appendLine("brokers: ${brokersDir.children().size}")
        appendLine("consumerGroups: ${consumerGroupsDir.children().size}")
        appendLine("schemaRegistry: ${schemaRegistryDir.children().size}")
        appendLine("topics: ${topicsDir.children().size}")
    }

    override suspend fun watch(): Unit = supervisorScope {
        val fsRefreshRate = kafkaConfig.fsRefreshRate.seconds
        logger.info { "launching kafka watchers (rate: $fsRefreshRate)" }

        // A broker outage makes every watcher tick fail. Don't flood the logs with a full stack
        // trace per tick (the 2026-05-31 incident produced ~18.9k of them): log the first
        // occurrence (and any change of error) at warn, and keep the rest at debug. Combined with
        // the exponential backoff below, a transient blip stays quiet.
        var lastTransientFailure: String? = null
        val exceptionHandler: (Throwable) -> Unit = { exception ->
            if (isTransientBrokerException(exception)) {
                val signature = exception::class.qualifiedName
                if (signature != lastTransientFailure) {
                    lastTransientFailure = signature
                    logger.warn { "kafka cluster '$name' is unreachable, backing off: ${exception.message}" }
                } else {
                    logger.debug(exception) { "kafka cluster directory watcher still failing" }
                }
            } else {
                lastTransientFailure = null
                logger.error(exception) { "kafka cluster directory watcher failed" }
            }
        }

        // Back off up to ~10x the refresh rate while the cluster stays unreachable.
        val maxBackoff = fsRefreshRate * 10

        tick(fsRefreshRate, exceptionHandler, maxBackoff) { refreshConsumerGroupsDir() }
        tick(fsRefreshRate, exceptionHandler, maxBackoff) { refreshBrokersDir() }
        tick(fsRefreshRate, exceptionHandler, maxBackoff) { refreshTopicsDir() }
        tick(fsRefreshRate, exceptionHandler, maxBackoff) { refreshSchemaRegistryDir() }
    }

    override fun refresh() {
        refreshBrokersDir()
        refreshConsumerGroupsDir()
        refreshSchemaRegistryDir()
        refreshTopicsDir()
    }

    private fun refreshBrokersDir() {
        logger.debug { "$name brokers refresh" }
        brokersDir.replaceAll(kafkaAdminClient.brokerIds().map { Broker(it) })
    }

    private fun refreshConsumerGroupsDir() {
        logger.debug { "$name consumer groups refresh" }
        consumerGroupsDir.replaceAll(kafkaAdminClient.consumerGroupIds().map { ConsumerGroup(it) })
    }

    private fun refreshTopicsDir() {
        logger.debug { "$name topics refresh" }
        topicsDir.replaceAll(kafkaAdminClient.topicNames()
            .filterNot {
                it.startsWith("typestream-app-") ||
                it.startsWith("__typestream") ||
                it.contains("-inspect-") ||
                it.endsWith("-stdout")  // Filter preview job output topics
            }
            .map { t -> Topic(t, kafkaAdminClient) })
    }


    private fun refreshSchemaRegistryDir() {
        logger.debug { "$name schema registry refresh" }
        schemaRegistryDir.replaceAll(schemaRegistryClient.subjects().keys.map { t -> Directory(t) })
    }
}

/**
 * Whether [throwable] (or any of its causes) is a transient broker-connectivity failure — a
 * connection refusal or a Kafka client error/timeout — as opposed to an unexpected bug. Admin
 * client calls surface these wrapped in [java.util.concurrent.ExecutionException], so we walk the
 * cause chain. `org.apache.kafka.common.errors.TimeoutException` is a `KafkaException`, so the
 * `KafkaException` check covers broker timeouts too.
 */
internal fun isTransientBrokerException(throwable: Throwable): Boolean {
    var e: Throwable? = throwable
    while (e != null) {
        when (e) {
            is java.net.ConnectException,
            is org.apache.kafka.common.KafkaException -> return true
        }
        e = e.cause
    }
    return false
}
