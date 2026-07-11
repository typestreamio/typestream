package io.typestream.compiler.node

import io.typestream.compiler.types.DataStream
import io.typestream.compiler.types.datastream.toAvroGenericRecord
import io.typestream.compiler.types.datastream.toAvroSchema
import io.typestream.compiler.types.schema.Schema
import org.apache.avro.generic.GenericRecord
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class NodeQdrantEnvelopeTest {

    private val node = NodeQdrantEnvelope(
        id = "env-1",
        collectionName = "help_articles",
        idField = "id",
        vectorField = "embedding",
        payloadFields = listOf("title", "body")
    )

    private fun helpArticle() = DataStream(
        "/dev/kafka/local/topics/help_articles",
        Schema.Struct(
            listOf(
                Schema.Field("id", Schema.Long(7L)),
                Schema.Field("title", Schema.String("Free trial")),
                Schema.Field("body", Schema.String("The free trial lasts 14 days.")),
                Schema.Field("category", Schema.String("billing")),
                Schema.Field("embedding", Schema.List(listOf(Schema.Float(0.1f), Schema.Float(0.2f)), Schema.Float(0.0f)))
            )
        )
    )

    @Test
    fun `reshape builds the qdrant-kafka envelope`() {
        val reshaped = node.reshape(helpArticle())

        val schema = reshaped.schema as Schema.Struct
        assertThat(schema.value.map { it.name }).containsExactly("collection_name", "id", "vector", "payload")
        assertThat(schema["collection_name"]).isEqualTo(Schema.String("help_articles"))
        assertThat(schema["id"]).isEqualTo(Schema.Long(7L))
        assertThat(schema["vector"]).isEqualTo(
            Schema.List(listOf(Schema.Float(0.1f), Schema.Float(0.2f)), Schema.Float(0.0f))
        )

        val payload = schema["payload"] as Schema.Struct
        assertThat(payload.value.map { it.name }).containsExactly("title", "body")
        assertThat(payload["title"]).isEqualTo(Schema.String("Free trial"))
    }

    @Test
    fun `reshape with empty payload fields keeps everything except id and vector`() {
        val allFieldsNode = node.copy(payloadFields = emptyList())

        val reshaped = allFieldsNode.reshape(helpArticle())

        val payload = (reshaped.schema as Schema.Struct)["payload"] as Schema.Struct
        assertThat(payload.value.map { it.name }).containsExactly("title", "body", "category")
    }

    @Test
    fun `reshaped stream serializes to avro with nested payload record`() {
        val reshaped = node.reshape(helpArticle())

        // The intermediate topic is schemaless JSON at runtime, but the nested payload
        // struct and float-array vector must also survive the Avro write path
        // (preview/inspection topics stay Avro).
        val avroSchema = reshaped.toAvroSchema()
        assertThat(avroSchema.getField("payload").schema().type).isEqualTo(org.apache.avro.Schema.Type.RECORD)
        assertThat(avroSchema.getField("vector").schema().type).isEqualTo(org.apache.avro.Schema.Type.ARRAY)

        val record = reshaped.toAvroGenericRecord()
        assertThat(record.get("collection_name").toString()).isEqualTo("help_articles")
        assertThat(record.get("id")).isEqualTo(7L)

        @Suppress("UNCHECKED_CAST")
        val vector = record.get("vector") as List<Any?>
        assertThat(vector).containsExactly(0.1f, 0.2f)

        val payload = record.get("payload") as GenericRecord
        assertThat(payload.get("title").toString()).isEqualTo("Free trial")
        assertThat(payload.get("body").toString()).isEqualTo("The free trial lasts 14 days.")
    }

    @Test
    fun `reshaped stream serializes to the schemaless json envelope`() {
        val reshaped = node.reshape(helpArticle())

        // This is what the JSON sink path writes to the intermediate topic and what
        // qdrant-kafka's JsonConverter (schemas.enable=false) happy path consumes.
        val json = reshaped.schema.toJsonElement().toString()
        assertThat(json).isEqualTo(
            """{"collection_name":"help_articles","id":7,"vector":[0.1,0.2],"payload":{"title":"Free trial","body":"The free trial lasts 14 days."}}"""
        )
    }
}
