package io.typestream.kafka

import io.typestream.compiler.types.DataStream
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.apache.kafka.common.serialization.Deserializer
import org.apache.kafka.common.serialization.Serde
import org.apache.kafka.common.serialization.Serializer
import java.nio.charset.Charset

class DataStreamSerde : Serde<DataStream>, Deserializer<DataStream>, Serializer<DataStream> {
    override fun serialize(topic: String?, data: DataStream?) = Json.encodeToString(data).toByteArray()

    override fun deserialize(topic: String?, data: ByteArray?): DataStream? {
        if (data == null) {
            return null
        }
        return Json.decodeFromString<DataStream>(String(data, Charset.forName("UTF8")))
    }

    override fun serializer(): Serializer<DataStream> = this
    override fun deserializer(): Deserializer<DataStream> = this

    override fun close() {}
    override fun configure(configs: MutableMap<String, *>?, isKey: Boolean) {}
}
