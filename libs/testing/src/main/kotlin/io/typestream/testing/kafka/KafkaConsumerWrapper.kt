package io.typestream.testing.kafka

import io.confluent.kafka.serializers.KafkaAvroDeserializer
import io.confluent.kafka.serializers.KafkaAvroDeserializerConfig
import io.github.oshai.kotlinlogging.KotlinLogging
import org.apache.avro.specific.SpecificRecordBase
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.consumer.ConsumerRecords
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.common.serialization.StringDeserializer
import org.checkerframework.checker.units.qual.K
import java.time.Duration
import java.util.Properties
import java.util.UUID

class KafkaConsumerWrapper(private val boostrapServers: String, private val schemaRegistryUrl: String) {
    private val logger = KotlinLogging.logger { }

    fun consume(recordsToFetch: List<RecordsExpected>): MutableList<ConsumerRecord<String, SpecificRecordBase>> {
        val expected = recordsToFetch.associateBy({ it.topic }, { it.expected })
        val fetched = recordsToFetch.associateBy({ it.topic }, { 0 }).toMutableMap()
        val recordsToReturn: MutableList<ConsumerRecord<String, SpecificRecordBase>> = ArrayList()
        KafkaConsumer<String, SpecificRecordBase>(consumerConfig()).use { consumer ->
            consumer.subscribe(recordsToFetch.map { it.topic })
            var retries = 0
            do {
                logger.debug { "retry: $retries" }
                val records: ConsumerRecords<String, SpecificRecordBase> = consumer.poll(Duration.ofMillis(1000))
                for (record in records) {
                    if (fetched[record.topic()]!! < expected[record.topic()]!!) {
                        recordsToReturn.add(record)
                        fetched[record.topic()] = fetched[record.topic()]!! + 1
                    }
                }
                logger.debug { "fetched: $fetched" }
            } while ((retries++ < 30) and (fetched.values.sum() < expected.values.sum()))
            consumer.unsubscribe()
        }

        if (fetched.values.sum() < expected.values.sum()) {
            throw RuntimeException("could not consume on time. Fetched: $fetched Expected: $expected")
        }

        return recordsToReturn
    }

    private fun consumerConfig(): Properties {
        val consumerProps = Properties()
        consumerProps[ConsumerConfig.AUTO_OFFSET_RESET_CONFIG] = "earliest"
        consumerProps[ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG] = boostrapServers
        consumerProps[ConsumerConfig.GROUP_ID_CONFIG] = UUID.randomUUID().toString()
        consumerProps[ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG] = StringDeserializer::class.java
        consumerProps[ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG] = KafkaAvroDeserializer::class.java
        consumerProps[KafkaAvroDeserializerConfig.SCHEMA_REGISTRY_URL_CONFIG] = schemaRegistryUrl
        consumerProps[KafkaAvroDeserializerConfig.SPECIFIC_AVRO_READER_CONFIG] = true
        return consumerProps
    }
}
