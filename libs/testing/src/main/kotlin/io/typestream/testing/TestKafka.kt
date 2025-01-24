package io.typestream.testing

import io.typestream.testing.kafka.AdminClientWrapper
import io.typestream.testing.kafka.KafkaProducerWrapper
import io.typestream.testing.model.TestRecord
import org.testcontainers.redpanda.RedpandaContainer

class TestKafka : RedpandaContainer("docker.redpanda.com/vectorized/redpanda:v24.2.9") {
    fun produceRecords(topic: String, encoding: String, vararg records: TestRecord): List<TestRecord> {
        val adminClient = AdminClientWrapper(bootstrapServers)
        adminClient.createTopics(topic)

        val kafkaProducer = KafkaProducerWrapper(bootstrapServers, schemaRegistryAddress)

        return kafkaProducer.produce(topic, encoding, records.toList())
    }
}
