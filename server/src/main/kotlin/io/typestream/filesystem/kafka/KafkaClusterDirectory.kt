package io.typestream.filesystem.kafka

import io.github.oshai.kotlinlogging.KotlinLogging
import io.typestream.config.KafkaConfig
import io.typestream.coroutine.tick
import io.typestream.filesystem.Directory
import io.typestream.kafka.KafkaAdminClient
import io.typestream.kafka.schemaregistry.SchemaRegistryClient
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.supervisorScope
import kotlin.time.Duration.Companion.seconds


class KafkaClusterDirectory(
    name: String,
    private val kafkaConfig: KafkaConfig,
    private val dispatcher: CoroutineDispatcher,
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

        val handler = CoroutineExceptionHandler { _, exception ->
            logger.error(exception) { "kafka cluster directory watcher failed" }
        }

        val networkExceptionHandler: (Throwable) -> Unit = { exception ->
            when (exception) {
                is java.net.ConnectException -> logger.debug(exception) { "kafka cluster directory watcher failed" }
                else -> throw exception
            }
        }

        val scope = CoroutineScope(dispatcher + handler)

        scope.tick(fsRefreshRate, networkExceptionHandler) {
            refreshConsumerGroupsDir()
        }

        scope.tick(fsRefreshRate, networkExceptionHandler) {
            refreshBrokersDir()
        }

        scope.tick(fsRefreshRate, networkExceptionHandler) {
            refreshTopicsDir()
        }

        scope.tick(fsRefreshRate, networkExceptionHandler) {
            refreshSchemaRegistryDir()
        }
    }

    override fun refresh() {
        refreshBrokersDir()
        refreshConsumerGroupsDir()
        refreshSchemaRegistryDir()
        refreshTopicsDir()
    }

    private fun refreshBrokersDir() {
        logger.info { "$name brokers refresh" }
        brokersDir.replaceAll(kafkaAdminClient.brokerIds().map { Broker(it) })
    }

    private fun refreshConsumerGroupsDir() {
        logger.info { "$name consumer groups refresh" }
        consumerGroupsDir.replaceAll(kafkaAdminClient.consumerGroupIds().map { ConsumerGroup(it) })
    }

    private fun refreshTopicsDir() {
        logger.info { "$name topics refresh" }
        topicsDir.replaceAll(kafkaAdminClient.topicNames()
            .filterNot {
                it.startsWith("typestream-app-") ||
                it.contains("-inspect-") ||
                it.endsWith("-stdout")  // Filter preview job output topics
            }
            .map { t -> Topic(t, kafkaAdminClient) })
    }


    private fun refreshSchemaRegistryDir() {
        logger.info { "$name schema registry refresh" }
        schemaRegistryDir.replaceAll(schemaRegistryClient.subjects().keys.map { t -> Directory(t) })
    }
}
