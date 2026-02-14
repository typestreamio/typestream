package io.typestream.pipeline

import com.google.protobuf.util.JsonFormat
import io.github.oshai.kotlinlogging.KotlinLogging
import io.typestream.config.KafkaConfig
import io.typestream.grpc.job_service.Job.PipelineGraph
import io.typestream.grpc.job_service.Job.UserPipelineGraph
import io.typestream.grpc.pipeline_service.Pipeline.PipelineMetadata
import kotlinx.serialization.json.Json
import org.apache.kafka.clients.admin.Admin
import org.apache.kafka.clients.admin.AdminClientConfig
import org.apache.kafka.clients.admin.NewTopic
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.config.SaslConfigs
import org.apache.kafka.common.config.TopicConfig
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.kafka.common.serialization.StringSerializer
import java.io.Closeable
import java.time.Duration
import java.util.Properties
import java.util.UUID
import java.util.concurrent.TimeUnit

data class PipelineRecord(
    val metadata: PipelineMetadata,
    val userGraph: UserPipelineGraph,
    val graph: PipelineGraph,
    val appliedAt: Long
)

class PipelineStateStore(private val kafkaConfig: KafkaConfig) : Closeable {

    private val logger = KotlinLogging.logger {}
    private val json = Json { ignoreUnknownKeys = true }
    private val protoJsonPrinter = JsonFormat.printer().omittingInsignificantWhitespace()
    private val protoJsonParser = JsonFormat.parser().ignoringUnknownFields()

    private val producer: KafkaProducer<String, String>
    private val admin: Admin

    companion object {
        const val TOPIC_NAME = "__typestream_pipelines"
    }

    init {
        val props = baseProps()
        props[ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG] = StringSerializer::class.java.name
        props[ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG] = StringSerializer::class.java.name
        props[ProducerConfig.ACKS_CONFIG] = "all"
        producer = KafkaProducer(props)

        val adminProps = baseProps()
        admin = Admin.create(adminProps)
    }

    private fun baseProps(): Properties {
        val props = Properties()
        props[AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG] = kafkaConfig.bootstrapServers
        kafkaConfig.saslConfig?.let {
            props[AdminClientConfig.SECURITY_PROTOCOL_CONFIG] = "SASL_SSL"
            props[SaslConfigs.SASL_MECHANISM] = it.mechanism
            props[SaslConfigs.SASL_JAAS_CONFIG] = it.jaasConfig
        }
        return props
    }

    fun ensureTopicExists() {
        val existingTopics = admin.listTopics().names().get()
        if (TOPIC_NAME in existingTopics) {
            logger.info { "Pipeline state topic '$TOPIC_NAME' already exists" }
            return
        }

        val topicConfig = mapOf(
            TopicConfig.CLEANUP_POLICY_CONFIG to TopicConfig.CLEANUP_POLICY_COMPACT
        )
        val newTopic = NewTopic(TOPIC_NAME, 1, 1.toShort()).configs(topicConfig)

        try {
            admin.createTopics(listOf(newTopic)).all().get()
            logger.info { "Created pipeline state topic '$TOPIC_NAME'" }
        } catch (e: Exception) {
            // If the topic was created between our check and create attempt, that's fine
            if (e.cause is org.apache.kafka.common.errors.TopicExistsException) {
                logger.info { "Pipeline state topic '$TOPIC_NAME' already exists" }
            } else {
                throw e
            }
        }
    }

    fun load(): Map<String, PipelineRecord> {
        val props = baseProps()
        props[ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG] = StringDeserializer::class.java.name
        props[ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG] = StringDeserializer::class.java.name
        props[ConsumerConfig.GROUP_ID_CONFIG] = "typestream-state-loader-${UUID.randomUUID()}"
        props[ConsumerConfig.AUTO_OFFSET_RESET_CONFIG] = "earliest"
        props[ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG] = "false"

        val consumer = KafkaConsumer<String, String>(props)
        val result = mutableMapOf<String, PipelineRecord>()

        try {
            val partitions = consumer.partitionsFor(TOPIC_NAME)
            if (partitions == null || partitions.isEmpty()) {
                logger.info { "No partitions found for $TOPIC_NAME" }
                return emptyMap()
            }

            val topicPartitions = partitions.map {
                org.apache.kafka.common.TopicPartition(it.topic(), it.partition())
            }
            consumer.assign(topicPartitions)
            consumer.seekToBeginning(topicPartitions)

            val endOffsets = consumer.endOffsets(topicPartitions)
            val allAtEnd = endOffsets.all { it.value == 0L }
            if (allAtEnd) {
                logger.info { "Pipeline state topic is empty" }
                return emptyMap()
            }

            while (true) {
                val records = consumer.poll(Duration.ofSeconds(2))
                for (record in records) {
                    if (record.value() == null) {
                        // Tombstone - pipeline was deleted
                        result.remove(record.key())
                    } else {
                        try {
                            result[record.key()] = deserialize(record.value())
                        } catch (e: Exception) {
                            logger.error(e) { "Failed to deserialize pipeline record: ${record.key()}" }
                        }
                    }
                }

                // Check if we've reached the end
                val currentPositions = topicPartitions.associateWith { consumer.position(it) }
                val reachedEnd = endOffsets.all { (tp, end) -> (currentPositions[tp] ?: 0) >= end }
                if (reachedEnd || records.isEmpty) {
                    break
                }
            }
        } catch (e: org.apache.kafka.common.errors.UnknownTopicOrPartitionException) {
            logger.info { "Pipeline state topic does not exist yet" }
            return emptyMap()
        } finally {
            consumer.close()
        }

        logger.info { "Loaded ${result.size} pipeline(s) from state store" }
        return result
    }

    fun save(name: String, record: PipelineRecord) {
        val value = serialize(record)
        producer.send(ProducerRecord(TOPIC_NAME, name, value)).get(30, TimeUnit.SECONDS)
        logger.info { "Saved pipeline '$name' to state store" }
    }

    fun delete(name: String) {
        producer.send(ProducerRecord(TOPIC_NAME, name, null)).get(30, TimeUnit.SECONDS)
        logger.info { "Deleted pipeline '$name' from state store (tombstone)" }
    }

    private fun serialize(record: PipelineRecord): String {
        val metadataJson = protoJsonPrinter.print(record.metadata)
        val userGraphJson = protoJsonPrinter.print(record.userGraph)
        val graphJson = protoJsonPrinter.print(record.graph)
        return """{"metadata":$metadataJson,"userGraph":$userGraphJson,"graph":$graphJson,"appliedAt":${record.appliedAt}}"""
    }

    private fun deserialize(value: String): PipelineRecord {
        val root = json.parseToJsonElement(value).let { it as kotlinx.serialization.json.JsonObject }

        val metadataJson = root["metadata"].toString()
        val metadataBuilder = PipelineMetadata.newBuilder()
        protoJsonParser.merge(metadataJson, metadataBuilder)

        val userGraphBuilder = UserPipelineGraph.newBuilder()
        val userGraphRaw = root["userGraph"]
        if (userGraphRaw != null) {
            protoJsonParser.merge(userGraphRaw.toString(), userGraphBuilder)
        }

        val graphJson = root["graph"].toString()
        val graphBuilder = PipelineGraph.newBuilder()
        protoJsonParser.merge(graphJson, graphBuilder)

        val appliedAt = root["appliedAt"]?.let {
            (it as kotlinx.serialization.json.JsonPrimitive).content.toLong()
        } ?: System.currentTimeMillis()

        return PipelineRecord(
            metadata = metadataBuilder.build(),
            userGraph = userGraphBuilder.build(),
            graph = graphBuilder.build(),
            appliedAt = appliedAt
        )
    }

    override fun close() {
        producer.close()
        admin.close()
    }
}
