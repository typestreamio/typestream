package io.typestream.kafka

import com.google.protobuf.Message
import io.typestream.kafka.protobuf.ProtoParser
import io.typestream.kafka.protobuf.ProtoSchema
import io.typestream.kafka.protobuf.toMessage
import io.typestream.kafka.schemaregistry.SchemaRegistryClient
import io.typestream.kafka.schemaregistry.SchemaType
import org.apache.kafka.common.serialization.Deserializer
import org.apache.kafka.common.serialization.Serde
import org.apache.kafka.common.serialization.Serializer
import org.apache.kafka.common.utils.ByteUtils
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer


class ProtoSerde(private val schema: ProtoSchema) : Serde<Message>, Deserializer<Message>,
    Serializer<Message> {
    private lateinit var schemaRegistryClient: SchemaRegistryClient

    override fun serialize(topic: String, data: Message?): ByteArray? {
        if (data == null) {
            return null
        }

        val out = ByteArrayOutputStream()
        out.write(0) // magic byte
        val id = schemaRegistryClient.register(topic, SchemaType.PROTOBUF, schema.toString())
        out.write(ByteBuffer.allocate(4).putInt(id).array())

        //TODO handle nested Types
        val indexes = mutableListOf<Int>()
        schema.message.fields.forEachIndexed { i, _ ->
            indexes.add(i)
        }

        var size: Int = ByteUtils.sizeOfVarint(indexes.size)
        indexes.forEach { size += ByteUtils.sizeOfVarint(it) }
        val indexesBuffer = ByteBuffer.allocate(size)
        ByteUtils.writeVarint(indexes.size, indexesBuffer)
        indexes.forEach { ByteUtils.writeVarint(it, indexesBuffer) }
        out.write(indexesBuffer.array())

        data.writeTo(out)

        val bytes = out.toByteArray()

        out.close()
        return bytes
    }

    override fun deserialize(topic: String?, data: ByteArray?): Message? {
        if (data == null) {
            return null
        }

        val buffer = ByteBuffer.wrap(data)

        buffer.get() // skip magic byte
        val id = buffer.int

        val schema = ProtoParser.parse(schemaRegistryClient.schema(id))

        //TODO read the indexes to find the right message

        val size = ByteUtils.readVarint(buffer)
        repeat(size) {
            ByteUtils.readVarint(buffer)
        }

        val messageBytes = buffer.array().sliceArray(buffer.position() until buffer.limit())

        val message = schema.message.toMessage().newBuilderForType()
        message.mergeFrom(messageBytes)

        return message.build()
    }

    override fun serializer(): Serializer<Message> = this
    override fun deserializer(): Deserializer<Message> = this

    override fun close() {}

    override fun configure(configs: MutableMap<String, *>, isKey: Boolean) {
        configs["schema.registry.url"]?.let {
            schemaRegistryClient = SchemaRegistryClient(it.toString())
        }
    }
}
