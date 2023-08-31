package io.typestream.testing.kafka

import io.confluent.kafka.serializers.KafkaAvroSerializer
import io.confluent.kafka.serializers.KafkaAvroSerializerConfig
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.serialization.StringSerializer
import java.util.Properties

class KafkaProducerWrapper(private val bootstrapServers: String, private val schemaRegistryUrl: String) {
    fun <K, V> produce(records: List<ProducerRecord<K, V>>) {
        val producer = KafkaProducer<K, V>(producerConfig())

        for (e in records) {
            producer.send(e).get()
        }
    }

    private fun producerConfig(): Properties {
        val props = Properties()
        props[ProducerConfig.BOOTSTRAP_SERVERS_CONFIG] = bootstrapServers
        props[ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG] = StringSerializer::class.java
        props[ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG] = KafkaAvroSerializer::class.java
        props[KafkaAvroSerializerConfig.SCHEMA_REGISTRY_URL_CONFIG] = schemaRegistryUrl

        return props
    }
}
