package io.typestream.testing.kafka

import io.confluent.kafka.serializers.KafkaAvroSerializer
import io.confluent.kafka.serializers.KafkaAvroSerializerConfig
import io.confluent.kafka.serializers.protobuf.KafkaProtobufSerializer
import io.typestream.testing.model.TestRecord
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.serialization.StringSerializer
import java.util.Properties

class KafkaProducerWrapper(private val bootstrapServers: String, private val schemaRegistryUrl: String) {

    fun produce(topic: String, encoding: String, records: List<TestRecord>): List<TestRecord> {
        return when (encoding) {
            "avro" -> produceRecords(
                "avro",
                TestRecord.toAvroProducerRecords(topic, records.toList())
            ).map { TestRecord.fromAvro(it.topic(), it.key(), it.value()) }

            "proto" -> produceRecords(
                "proto",
                TestRecord.toProtoProducerRecords(topic, records.toList())
            ).map { TestRecord.fromProto(it.topic(), it.key(), it.value()) }

            else -> error("unsupported encoding: $encoding")
        }
    }

    private fun <K, V> produceRecords(
        encoding: String,
        records: List<ProducerRecord<K, V>>,
    ): List<ProducerRecord<K, V>> {
        val produced = mutableListOf<ProducerRecord<K, V>>()
        KafkaProducer<K, V>(producerConfig(encoding)).use {
            for (e in records) {
                it.send(e).get()
                produced.add(e)
            }
        }
        return produced
    }

    private fun producerConfig(encoding: String): Properties {
        val props = Properties()
        props[ProducerConfig.BOOTSTRAP_SERVERS_CONFIG] = bootstrapServers
        props[ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG] = StringSerializer::class.java
        props[ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG] =
            when (encoding) {
                "avro" -> KafkaAvroSerializer::class.java
                "proto" -> KafkaProtobufSerializer::class.java
                else -> error("unsupported encoding: $encoding")
            }
        props[KafkaAvroSerializerConfig.SCHEMA_REGISTRY_URL_CONFIG] = schemaRegistryUrl

        return props
    }
}
