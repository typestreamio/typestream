package io.typestream.kafka

import io.github.oshai.kotlinlogging.KotlinLogging
import io.typestream.config.KafkaConfig
import org.apache.kafka.clients.admin.Admin
import org.apache.kafka.clients.admin.AdminClientConfig
import org.apache.kafka.clients.admin.KafkaAdminClient
import java.util.Properties


class KafkaAdminClient(kafkaConfig: KafkaConfig) {
    private val logger = KotlinLogging.logger { }
    private val kafkaAdminClient: Admin

    init {
        val props = Properties()
        props[AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG] = kafkaConfig.bootstrapServers

        this.kafkaAdminClient = KafkaAdminClient.create(props)
        logger.info { "kafka admin created" }
    }

    fun topicNames(): Set<String> = kafkaAdminClient.listTopics().names().get()

    fun topicInfo(topicName: String): String {
        val topics = kafkaAdminClient.describeTopics(listOf(topicName)).allTopicNames().get()

        return """
            internal: ${topics[topicName]?.isInternal}
            partitions: ${topics[topicName]?.partitions()?.size}
        """.trimIndent()
    }

    fun consumerGroupIds() = kafkaAdminClient.listConsumerGroups().all().get().map { it.groupId() }.toSet()

    fun brokerIds() = kafkaAdminClient.describeCluster().nodes().get().map { it.idString() }.toSet()
}
