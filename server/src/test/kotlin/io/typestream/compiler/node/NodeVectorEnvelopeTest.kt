package io.typestream.compiler.node

import io.typestream.compiler.types.DataStream
import io.typestream.compiler.types.Encoding
import io.typestream.compiler.types.InferenceContext
import io.typestream.compiler.types.schema.Schema
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class NodeVectorEnvelopeTest {

    private val noOpContext = object : InferenceContext {
        override fun lookupDataStream(path: String): DataStream = error("not used")
        override fun lookupEncoding(path: String): Encoding = error("not used")
    }

    private fun helpArticle() = DataStream(
        "/dev/kafka/local/topics/help_article_embeddings",
        Schema.Struct(
            listOf(
                Schema.Field("id", Schema.Int(7)),
                Schema.Field("title", Schema.String("Free Trial")),
                Schema.Field("body", Schema.String("14-day free trial")),
                Schema.Field("embedding", Schema.List(listOf(Schema.Float(0.1f), Schema.Float(0.2f)), Schema.Float(0f))),
            )
        )
    )

    private val node = NodeVectorEnvelope(
        id = "envelope-1",
        idField = "id",
        vectorField = "embedding",
        idOut = "id",
        vectorOut = "vector",
        payloadOut = "payload",
    )

    @Test
    fun `reshape produces id, vector, and a payload of the remaining fields`() {
        val out = node.reshape(helpArticle()).schema as Schema.Struct

        assertThat(out.value.map { it.name }).containsExactly("id", "vector", "payload")
        assertThat(out["id"]).isEqualTo(Schema.Int(7))
        assertThat(out["vector"]).isInstanceOf(Schema.List::class.java)

        val payload = out["payload"] as Schema.Struct
        assertThat(payload.value.map { it.name }).containsExactly("title", "body")
        assertThat(payload["title"]).isEqualTo(Schema.String("Free Trial"))
    }

    @Test
    fun `inferOutputSchema forces JSON encoding`() {
        val result = node.inferOutputSchema(helpArticle(), Encoding.AVRO, noOpContext)
        assertThat(result.encoding).isEqualTo(Encoding.JSON)
    }

    @Test
    fun `reshaped record serializes to the clean Qdrant envelope`() {
        val json = node.reshape(helpArticle()).schema.toJsonElement().toString()
        assertThat(json).isEqualTo("""{"id":7,"vector":[0.1,0.2],"payload":{"title":"Free Trial","body":"14-day free trial"}}""")
    }
}
