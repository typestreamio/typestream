package io.typestream.testing.kafka

import io.confluent.kafka.serializers.KafkaAvroDeserializer
import io.confluent.kafka.serializers.KafkaAvroDeserializerConfig
import org.apache.avro.specific.SpecificRecordBase
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.consumer.ConsumerRecords
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.common.serialization.StringDeserializer
import java.time.Duration
import java.util.Properties
import java.util.UUID

class KafkaConsumerWrapper(private val boostrapServers: String, private val schemaRegistryUrl: String) {
    private fun <K : SpecificRecordBase> consumeRecords(
        topic: String,
        expected: Int,
    ): List<ConsumerRecord<String, K>> {
        var fetched = 0
        val recordsToReturn: MutableList<ConsumerRecord<String, K>> = ArrayList()
        val consumer = KafkaConsumer<String, K>(consumerConfig())
        consumer.subscribe(listOf(topic))
        var retries = 0
        do {
            val records: ConsumerRecords<String, K> = consumer.poll(Duration.ofMillis(1000))
            if (!records.isEmpty) {
                for (record in records) {
                    if (fetched < expected) {
                        recordsToReturn.add(record)
                        fetched++
                    }
                }
            }
        } while ((retries++ < 30) and (fetched < expected))
        consumer.unsubscribe()

        if (fetched < expected) {
            throw RuntimeException("could not consume from $topic on time. Fetched: $fetched Expected: $expected")
        }
        return recordsToReturn
    }

    fun <K : SpecificRecordBase> consume(topic: String, expected: Int): List<K> =
        consumeRecords<K>(topic, expected).map { it.value() }

    private fun consumerConfig(): Properties {
        val consumerProps = Properties()
        consumerProps[ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG] = boostrapServers
        consumerProps[ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG] = StringDeserializer::class.java
        consumerProps[ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG] = KafkaAvroDeserializer::class.java
        consumerProps[ConsumerConfig.GROUP_ID_CONFIG] = UUID.randomUUID().toString()
        consumerProps[KafkaAvroDeserializerConfig.SCHEMA_REGISTRY_URL_CONFIG] = schemaRegistryUrl
        consumerProps[KafkaAvroDeserializerConfig.SPECIFIC_AVRO_READER_CONFIG] = true
        consumerProps[ConsumerConfig.AUTO_OFFSET_RESET_CONFIG] = "earliest"
        return consumerProps
    }
}
