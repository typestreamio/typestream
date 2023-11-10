package io.typestream.kafka

import io.typestream.kafka.schemaregistry.SchemaRegistryClient
import io.typestream.kafka.schemaregistry.SchemaType.AVRO
import io.typestream.testing.TestKafka
import io.typestream.testing.model.User
import org.apache.avro.Schema.Parser
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers

@Testcontainers
internal class SchemaRegistryClientTest {

    @Container
    private val testKafka = TestKafka()

    @Test
    fun `fetches schemas`() {
        testKafka.produceRecords("users", "avro", User(name = "Margaret Hamilton"))

        val schemaRegistryClient = SchemaRegistryClient(testKafka.schemaRegistryAddress)

        val subjects = schemaRegistryClient.subjects()
        assertThat(subjects).hasSize(1).containsKey("users-value")

        val schema = subjects["users-value"]

        assertThat(schema).extracting("subject", "schemaType").containsExactly("users-value", AVRO)

        val avroSchema = Parser().parse(schema?.schema)

        assertThat(avroSchema).isEqualTo(io.typestream.testing.avro.User.`SCHEMA$`)
    }

}
