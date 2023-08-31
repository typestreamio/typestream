package io.typestream.filesystem.kafka

import io.github.oshai.kotlinlogging.KotlinLogging
import io.typestream.config.KafkaConfig
import io.typestream.coroutine.tick
import io.typestream.filesystem.Directory
import io.typestream.kafka.KafkaAdminClient
import io.typestream.kafka.schemaregistry.SchemaRegistryClient
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
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

    private val schemaRegistryClient = SchemaRegistryClient(kafkaConfig.schemaRegistryUrl)

    init {
        setOf(brokersDir, consumerGroupsDir, topicsDir, schemaRegistryDir).forEach(::add)
    }

    override fun stat() = buildString {
        appendLine("File: $name")

        appendLine("brokers: ${brokersDir.children().size}")
        appendLine("consumerGroups: ${consumerGroupsDir.children().size}")
        appendLine("topics: ${topicsDir.children().size}")
    }

    override suspend fun watch(): Unit = coroutineScope {
        val fsRefreshRate = kafkaConfig.fsRefreshRate.seconds
        launch(dispatcher) {
            tick(fsRefreshRate) {
                logger.info { "$name consumer groups refresh" }
                consumerGroupsDir.replaceAll(kafkaAdminClient.consumerGroupIds().map { ConsumerGroup(it) })
            }
        }

        launch(dispatcher) {
            tick(fsRefreshRate) {
                logger.info { "$name brokers refresh" }
                brokersDir.replaceAll(kafkaAdminClient.brokerIds().map { Broker(it) })
            }
        }

        launch(dispatcher) {
            tick(fsRefreshRate) {
                logger.info { "$name topics refresh" }
                topicsDir.replaceAll(kafkaAdminClient.topicNames().filterNot { it.startsWith("typestream-app-") }
                    .map { t -> Topic(t, kafkaAdminClient) })
            }
        }

        launch(dispatcher) {
            tick(fsRefreshRate) {
                logger.info { "$name schema registry refresh" }
                schemaRegistryDir.replaceAll(schemaRegistryClient.subjects().keys.map { t -> Directory(t) })
            }
        }
    }
}
