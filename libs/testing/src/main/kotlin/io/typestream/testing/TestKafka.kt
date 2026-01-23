package io.typestream.testing

import io.confluent.kafka.schemaregistry.client.CachedSchemaRegistryClient
import io.confluent.kafka.schemaregistry.avro.AvroSchema
import io.typestream.testing.kafka.AdminClientWrapper
import io.typestream.testing.kafka.KafkaProducerWrapper
import io.typestream.testing.model.TestRecord
import org.testcontainers.redpanda.RedpandaContainer
import java.util.UUID

class TestKafka : RedpandaContainer("docker.redpanda.com/redpandadata/redpanda:v24.2.9") {

    companion object {
        /**
         * Generates a unique topic name by appending a short UUID suffix.
         * Use this in tests to ensure topic isolation when running tests in parallel.
         *
         * Example: uniqueTopic("users") -> "users-a1b2c3d4"
         */
        fun uniqueTopic(baseName: String): String {
            val suffix = UUID.randomUUID().toString().take(8)
            return "$baseName-$suffix"
        }
    }

    fun produceRecords(topic: String, encoding: String, vararg records: TestRecord): List<TestRecord> {
        val adminClient = AdminClientWrapper(bootstrapServers)
        adminClient.createTopics(topic)

        val kafkaProducer = KafkaProducerWrapper(bootstrapServers, schemaRegistryAddress)

        return kafkaProducer.produce(topic, encoding, records.toList())
    }

    fun createTopic(topic: String) {
        val adminClient = AdminClientWrapper(bootstrapServers)
        adminClient.createTopics(topic)
    }

    fun registerSchema(subject: String, schemaJson: String) {
        val schemaRegistryClient = CachedSchemaRegistryClient(schemaRegistryAddress, 100)
        val avroSchema = org.apache.avro.Schema.Parser().parse(schemaJson)
        schemaRegistryClient.register(subject, AvroSchema(avroSchema))
    }
}
