package io.typestream.testing

import io.typestream.testing.avro.toProducerRecords
import io.typestream.testing.kafka.AdminClientWrapper
import io.typestream.testing.kafka.KafkaProducerWrapper
import org.apache.avro.specific.SpecificRecordBase
import org.apache.kafka.clients.producer.ProducerRecord
import org.testcontainers.redpanda.RedpandaContainer

class RedpandaContainerWrapper : RedpandaContainer("docker.redpanda.com/vectorized/redpanda:v23.2.5") {
    fun <K : SpecificRecordBase> produceRecords(topic: String, vararg records: K): List<ProducerRecord<String, K>> {
        val adminClient = AdminClientWrapper(bootstrapServers)
        adminClient.createTopics(topic)

        val kafkaProducer = KafkaProducerWrapper(bootstrapServers, schemaRegistryAddress)

        val producerRecords = toProducerRecords(topic, *records)

        kafkaProducer.produce(producerRecords)

        return producerRecords
    }
}
