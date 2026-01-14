package io.typestream.connectors.kafka

import io.confluent.kafka.serializers.KafkaAvroSerializer
import io.confluent.kafka.serializers.KafkaAvroSerializerConfig
import io.github.oshai.kotlinlogging.KotlinLogging
import io.typestream.connectors.Config
import org.apache.avro.specific.SpecificRecord
import org.apache.kafka.clients.admin.AdminClient
import org.apache.kafka.clients.admin.AdminClientConfig
import org.apache.kafka.clients.admin.AlterConfigOp
import org.apache.kafka.clients.admin.ConfigEntry
import org.apache.kafka.clients.admin.NewTopic
import org.apache.kafka.common.config.ConfigResource
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.serialization.StringSerializer
import java.util.concurrent.TimeUnit

class Producer(private val config: Config) : MessageSender {
    private val logger = KotlinLogging.logger {}

    private val producer: KafkaProducer<String, SpecificRecord>
    private val adminClient: AdminClient

    init {
        val producerProps = mapOf(
            ProducerConfig.BOOTSTRAP_SERVERS_CONFIG to config.bootstrapServers,
            ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG to StringSerializer::class.java.name,
            ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG to KafkaAvroSerializer::class.java.name,
            KafkaAvroSerializerConfig.SCHEMA_REGISTRY_URL_CONFIG to config.schemaRegistryUrl,
            ProducerConfig.ACKS_CONFIG to "all",
            ProducerConfig.LINGER_MS_CONFIG to "5",
            ProducerConfig.BATCH_SIZE_CONFIG to "16384",
        )

        val adminProps = mapOf(
            AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG to config.bootstrapServers,
        )

        producer = KafkaProducer(producerProps)
        adminClient = AdminClient.create(adminProps)

        ensureTopicExists()
    }

    private fun ensureTopicExists() {
        val existingTopics = adminClient.listTopics().names().get(30, TimeUnit.SECONDS)

        if (config.topic !in existingTopics) {
            logger.info { "Creating topic ${config.topic} with retention ${config.retentionMs}ms" }

            val topic = NewTopic(config.topic, 3, 1.toShort())
                .configs(
                    mapOf(
                        "retention.ms" to config.retentionMs.toString(),
                        "segment.ms" to (config.retentionMs / 3).toString(), // Roll segments faster than retention
                        "cleanup.policy" to "delete"
                    )
                )

            adminClient.createTopics(listOf(topic)).all().get(30, TimeUnit.SECONDS)
            logger.info { "Topic ${config.topic} created successfully" }
        } else {
            logger.info { "Topic ${config.topic} already exists, updating retention to ${config.retentionMs}ms" }
            updateTopicRetention()
        }
    }

    private fun updateTopicRetention() {
        val configResource = ConfigResource(ConfigResource.Type.TOPIC, config.topic)
        val alterOps = listOf(
            AlterConfigOp(ConfigEntry("retention.ms", config.retentionMs.toString()), AlterConfigOp.OpType.SET),
            AlterConfigOp(ConfigEntry("segment.ms", (config.retentionMs / 3).toString()), AlterConfigOp.OpType.SET),
        )

        adminClient.incrementalAlterConfigs(mapOf(configResource to alterOps))
            .all()
            .get(30, TimeUnit.SECONDS)

        logger.info { "Topic ${config.topic} retention updated to ${config.retentionMs}ms (segment.ms=${config.retentionMs / 3}ms)" }
    }

    override fun send(key: String, value: SpecificRecord) {
        val record = ProducerRecord(config.topic, key, value)
        producer.send(record) { metadata, exception ->
            if (exception != null) {
                logger.error(exception) { "Failed to send record to ${config.topic}" }
            } else {
                logger.debug { "Sent record to ${metadata.topic()}-${metadata.partition()}@${metadata.offset()}" }
            }
        }
    }

    fun flush() {
        producer.flush()
    }

    override fun close() {
        logger.info { "Closing producer..." }
        producer.flush()
        producer.close()
        adminClient.close()
    }
}
