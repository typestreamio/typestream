package io.typestream.testing

import io.confluent.kafka.schemaregistry.client.CachedSchemaRegistryClient
import io.confluent.kafka.schemaregistry.avro.AvroSchema
import io.typestream.testing.kafka.AdminClientWrapper
import io.typestream.testing.kafka.KafkaProducerWrapper
import io.typestream.testing.model.TestRecord
import org.testcontainers.redpanda.RedpandaContainer

class TestKafka : RedpandaContainer("docker.redpanda.com/redpandadata/redpanda:v24.2.9") {
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
