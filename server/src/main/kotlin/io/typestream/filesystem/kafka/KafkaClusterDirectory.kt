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
        logger.info { "launching kafka watchers (rate: $fsRefreshRate seconds)" }

        val exceptionHandler: (Throwable) -> Unit = { exception ->
            when (exception) {
                is java.net.ConnectException -> logger.debug(exception) { "kafka cluster directory watcher failed" }
                else -> logger.error(exception) { "kafka cluster directory watcher failed" }
            }
        }

        tick(fsRefreshRate, exceptionHandler) { refreshConsumerGroupsDir() }
        tick(fsRefreshRate, exceptionHandler) { refreshBrokersDir() }
        tick(fsRefreshRate, exceptionHandler) { refreshTopicsDir() }
        tick(fsRefreshRate, exceptionHandler) { refreshSchemaRegistryDir() }
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
