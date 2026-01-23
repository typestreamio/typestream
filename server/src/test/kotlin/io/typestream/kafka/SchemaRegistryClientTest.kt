package io.typestream.kafka

import io.typestream.config.SchemaRegistryConfig
import io.typestream.kafka.schemaregistry.SchemaRegistryClient
import io.typestream.kafka.schemaregistry.SchemaType.AVRO
import io.typestream.testing.TestKafka
import io.typestream.testing.TestKafkaContainer
import io.typestream.testing.avro.User.getClassSchema
import io.typestream.testing.model.User
import org.apache.avro.Schema.Parser
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.testcontainers.junit.jupiter.Testcontainers

@Testcontainers
internal class SchemaRegistryClientTest {

    companion object {
        private val testKafka = TestKafkaContainer.instance
    }

    @Test
    fun `fetches schemas`() {
        val topic = TestKafka.uniqueTopic("users")
        testKafka.produceRecords(topic, "avro", User(name = "Margaret Hamilton"))

        val schemaRegistryClient = SchemaRegistryClient(SchemaRegistryConfig(testKafka.schemaRegistryAddress))

        val subjects = schemaRegistryClient.subjects()
        assertThat(subjects).containsKey("$topic-value")

        val schema = subjects["$topic-value"]

        assertThat(schema).extracting("subject", "schemaType").containsExactly("$topic-value", AVRO)

        val avroSchema = Parser().parse(schema?.schema)

        assertThat(avroSchema).isEqualTo(getClassSchema())
    }
}
