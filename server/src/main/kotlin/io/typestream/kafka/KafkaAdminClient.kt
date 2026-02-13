package io.typestream.kafka

import io.github.oshai.kotlinlogging.KotlinLogging
import io.typestream.config.KafkaConfig
import org.apache.kafka.clients.admin.Admin
import org.apache.kafka.clients.admin.AdminClientConfig
import org.apache.kafka.clients.admin.KafkaAdminClient
import org.apache.kafka.common.config.SaslConfigs
import java.io.Closeable
import java.time.Duration
import java.util.Properties


class KafkaAdminClient(kafkaConfig: KafkaConfig) : Closeable {
    private val logger = KotlinLogging.logger { }
    private val kafkaAdminClient: Admin

    init {
        val props = Properties()
        props[AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG] = kafkaConfig.bootstrapServers

        kafkaConfig.saslConfig?.let {
            props[AdminClientConfig.SECURITY_PROTOCOL_CONFIG] = "SASL_SSL"
            props[SaslConfigs.SASL_MECHANISM] = it.mechanism
            props[SaslConfigs.SASL_JAAS_CONFIG] = it.jaasConfig
        }

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

    fun deleteTopics(topics: Collection<String>) {
        logger.info { "deleting topics: $topics" }
        kafkaAdminClient.deleteTopics(topics).all().get()
    }

    override fun close() {
        kafkaAdminClient.close(Duration.ofSeconds(2))
    }
}
