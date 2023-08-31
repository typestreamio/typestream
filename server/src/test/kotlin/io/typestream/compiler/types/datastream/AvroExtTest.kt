package io.typestream.compiler.types.datastream

import io.typestream.compiler.types.DataStream
import io.typestream.compiler.types.schema.Schema
import io.typestream.helpers.author
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.tuple
import org.junit.jupiter.api.Test
import java.util.UUID

internal class AvroExtTest {

    @Test
    fun `converts from AvroSchema`() {
        val dataStream =
            DataStream.fromAvroSchema("/dev/kafka/local/topics/users", io.typestream.testing.avro.User.`SCHEMA$`)

        assertThat(dataStream).extracting("path").isEqualTo("/dev/kafka/local/topics/users")

        val schema = dataStream.schema

        require(schema is Schema.Struct)

        val values = schema.value

        assertThat(values).hasSize(2)
            .extracting("name", "value")
            .contains(
                tuple("id", Schema.String.empty),
                tuple("name", Schema.String.empty)
            )
    }

    @Test
    fun `converts from GenericAvro`() {
        val id = UUID.randomUUID()
        val authorId = UUID.randomUUID()
        val book = io.typestream.testing.avro.Book.newBuilder()
            .setTitle("Station eleven")
            .setWordCount(42)
            .setAuthorId(authorId)
            .setId(id)
            .build()

        val dataStream = DataStream.fromAvroGenericRecord("/dev/kafka/local/topics/books", book)

        assertThat(dataStream).extracting("path").isEqualTo("/dev/kafka/local/topics/books")

        val schema = dataStream.schema

        require(schema is Schema.Struct)

        val values = schema.value

        assertThat(values).hasSize(4)
            .extracting("name", "value")
            .contains(
                tuple("id", Schema.String(id.toString())),
                tuple("title", Schema.String("Station eleven")),
                tuple("author_id", Schema.String(authorId.toString())),
                tuple("word_count", Schema.Int(42)),
            )
    }

    @Test
    fun `converts to Avro Schema`() {
        val dataStream = author()

        assertThat(dataStream.toAvroSchema().toString()).isEqualTo(
            """
                {
                  "type": "record",
                  "name": "_dev_kafka_local_topics_authors",
                  "namespace": "franz.avro",
                  "fields": [ 
                      {
                        "name": "id",
                        "type": {"type":"string","logicalType":"uuid"}
                      }, 
                      {
                        "name": "name",
                        "type": {"type":"string","avro.java.string":"String"}
                      }
                  ]
                }
            """.replace("\\s+".toRegex(), "").trimIndent()
        )
    }

    @Test
    fun `converts to generic record`() {
        val id = UUID.randomUUID()
        val author = author(Schema.UUID(id), Schema.String("Emily St. John Mandel"))

        val genericRecord = author.toAvroGenericRecord()

        assertThat(genericRecord.get("id")).isEqualTo(id)
        assertThat(genericRecord.get("name")).isEqualTo("Emily St. John Mandel")
    }
}
