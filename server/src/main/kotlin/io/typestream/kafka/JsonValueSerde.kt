package io.typestream.kafka

import io.typestream.compiler.types.DataStream
import org.apache.kafka.common.serialization.Deserializer
import org.apache.kafka.common.serialization.Serde
import org.apache.kafka.common.serialization.Serializer

/**
 * Serializes a [DataStream] value as plain JSON reflecting its literal field structure
 * (via [io.typestream.compiler.types.schema.Schema.toJsonElement]), e.g.
 * `{"id":1,"vector":[...],"payload":{...}}`.
 *
 * Unlike [DataStreamSerde] (which serializes the internal DataStream wrapper), this emits
 * exactly the JSON an external sink connector expects. Used for sinks marked `cleanJson`
 * (e.g. the Qdrant sink, consumed by a JsonConverter with `schemas.enable=false`).
 *
 * Deserialization is unsupported: these topics are consumed by Kafka Connect, not by TypeStream.
 */
class JsonValueSerde : Serde<DataStream>, Serializer<DataStream> {
    override fun serialize(topic: String?, data: DataStream?): ByteArray? =
        data?.schema?.toJsonElement()?.toString()?.toByteArray()

    override fun serializer(): Serializer<DataStream> = this

    override fun deserializer(): Deserializer<DataStream> =
        throw UnsupportedOperationException("JsonValueSerde is write-only")

    override fun close() {}
    override fun configure(configs: MutableMap<String, *>?, isKey: Boolean) {}
}
