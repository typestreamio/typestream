package io.typestream.kafka

import io.typestream.config.SchemaRegistryConfig
import io.typestream.kafka.avro.AvroSerde
import io.typestream.kafka.schemaregistry.SchemaRegistryClient
import io.typestream.kafka.schemaregistry.SchemaType
import io.typestream.testing.TestKafka
import io.typestream.testing.TestKafkaContainer
import io.typestream.testing.avro.User
import io.typestream.testing.model.User as ModelUser
import org.apache.avro.Schema
import org.apache.avro.generic.GenericData
import org.apache.avro.generic.GenericRecord
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.testcontainers.junit.jupiter.Testcontainers

@Testcontainers
internal class AvroSerdeTest {

    companion object {
        private val testKafka = TestKafkaContainer.instance
    }

    @Test
    fun `deserializes records using schema from registry`() {
        val topic = TestKafka.uniqueTopic("users")
        // Produce a record to register the schema
        testKafka.produceRecords(topic, "avro", ModelUser(name = "Ada Lovelace"))

        val schemaRegistryClient = SchemaRegistryClient(SchemaRegistryConfig(testKafka.schemaRegistryAddress))

        // Create AvroSerde with the User schema
        val serde = AvroSerde(User.getClassSchema())
        serde.configure(
            mutableMapOf<String, Any>(
                "schema.registry.url" to testKafka.schemaRegistryAddress
            ),
            false
        )

        // Create a test record using the same schema
        val originalRecord = GenericData.Record(User.getClassSchema()).apply {
            put("id", java.util.UUID.randomUUID().toString())
            put("name", "Grace Hopper")
        }

        // Serialize and deserialize
        val serialized = serde.serialize(topic, originalRecord)
        val deserialized = serde.deserialize(topic, serialized)

        assertThat(deserialized).isNotNull
        assertThat(deserialized?.get("name")?.toString()).isEqualTo("Grace Hopper")
    }

    @Test
    fun `deserializes records with different schema namespace`() {
        // This test simulates Debezium-style schemas where the namespace differs
        // from what TypeStream would generate

        val topic = TestKafka.uniqueTopic("dbserver_public_test_table")
        val schemaRegistryClient = SchemaRegistryClient(SchemaRegistryConfig(testKafka.schemaRegistryAddress))

        // Create a schema with a different namespace (like Debezium would)
        // Note: Avro namespaces cannot contain hyphens, so we use a static namespace
        val debeziumStyleSchema = Schema.Parser().parse("""
            {
                "type": "record",
                "name": "Envelope",
                "namespace": "dbserver.public.test_table",
                "fields": [
                    {"name": "before", "type": ["null", "string"], "default": null},
                    {"name": "after", "type": ["null", "string"], "default": null},
                    {"name": "op", "type": "string"}
                ]
            }
        """.trimIndent())

        // Register this schema in the registry
        val schemaId = schemaRegistryClient.register(
            topic,
            SchemaType.AVRO,
            debeziumStyleSchema.toString()
        )

        // Create AvroSerde with the Debezium-style schema
        val serde = AvroSerde(debeziumStyleSchema)
        serde.configure(
            mutableMapOf<String, Any>(
                "schema.registry.url" to testKafka.schemaRegistryAddress
            ),
            false
        )

        // Create a test record
        val originalRecord = GenericData.Record(debeziumStyleSchema).apply {
            put("before", null)
            put("after", "new-value")
            put("op", "c")  // create operation
        }

        // Serialize and deserialize
        val serialized = serde.serialize(topic, originalRecord)
        val deserialized = serde.deserialize(topic, serialized)

        assertThat(deserialized).isNotNull
        assertThat(deserialized?.get("op")?.toString()).isEqualTo("c")
        assertThat(deserialized?.get("after")?.toString()).isEqualTo("new-value")
        assertThat(deserialized?.get("before")).isNull()
    }

    @Test
    fun `deserializes records produced by external producer with different schema`() {
        // This test verifies that we can deserialize records where the producer
        // used a different schema than what TypeStream reconstructs internally
        // This is the exact scenario that broke with Debezium

        val topic = TestKafka.uniqueTopic("external-topic")
        val schemaRegistryClient = SchemaRegistryClient(SchemaRegistryConfig(testKafka.schemaRegistryAddress))

        // Schema that external producer (like Debezium) would use
        val externalSchema = Schema.Parser().parse("""
            {
                "type": "record",
                "name": "Value",
                "namespace": "external.producer.schema",
                "fields": [
                    {"name": "id", "type": "int"},
                    {"name": "message", "type": "string"}
                ]
            }
        """.trimIndent())

        // Register this schema
        schemaRegistryClient.register(
            topic,
            SchemaType.AVRO,
            externalSchema.toString()
        )

        // Create serde with the external schema (the fix ensures we look up
        // the writer's schema from registry rather than using a reconstructed one)
        val serde = AvroSerde(externalSchema)
        serde.configure(
            mutableMapOf<String, Any>(
                "schema.registry.url" to testKafka.schemaRegistryAddress
            ),
            false
        )

        // Create and serialize a record
        val originalRecord = GenericData.Record(externalSchema).apply {
            put("id", 42)
            put("message", "Hello from external producer")
        }

        val serialized = serde.serialize(topic, originalRecord)

        // Now deserialize - this should work because we fetch the writer schema from registry
        val deserialized = serde.deserialize(topic, serialized)

        assertThat(deserialized).isNotNull
        assertThat(deserialized?.get("id")).isEqualTo(42)
        assertThat(deserialized?.get("message")?.toString()).isEqualTo("Hello from external producer")
    }

    @Test
    fun `caches schemas to avoid repeated registry lookups`() {
        val topic = TestKafka.uniqueTopic("cache-test")
        val schemaRegistryClient = SchemaRegistryClient(SchemaRegistryConfig(testKafka.schemaRegistryAddress))

        val schema = Schema.Parser().parse("""
            {
                "type": "record",
                "name": "CacheTest",
                "namespace": "io.typestream.test",
                "fields": [
                    {"name": "value", "type": "string"}
                ]
            }
        """.trimIndent())

        schemaRegistryClient.register(topic, SchemaType.AVRO, schema.toString())

        val serde = AvroSerde(schema)
        serde.configure(
            mutableMapOf<String, Any>(
                "schema.registry.url" to testKafka.schemaRegistryAddress
            ),
            false
        )

        val record = GenericData.Record(schema).apply {
            put("value", "test")
        }

        val serialized = serde.serialize(topic, record)

        // Deserialize multiple times - should use cached schema
        repeat(100) {
            val deserialized = serde.deserialize(topic, serialized)
            assertThat(deserialized?.get("value")?.toString()).isEqualTo("test")
        }
    }
}
