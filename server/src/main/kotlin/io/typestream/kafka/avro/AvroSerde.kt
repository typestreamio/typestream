package io.typestream.kafka.avro

import io.typestream.config.SchemaRegistryConfig
import io.typestream.kafka.schemaregistry.SchemaRegistryClient
import io.typestream.kafka.schemaregistry.SchemaType
import org.apache.avro.Schema
import org.apache.avro.generic.GenericDatumReader
import org.apache.avro.generic.GenericDatumWriter
import org.apache.avro.generic.GenericRecord
import org.apache.avro.io.DecoderFactory
import org.apache.avro.io.EncoderFactory
import org.apache.kafka.common.serialization.Deserializer
import org.apache.kafka.common.serialization.Serde
import org.apache.kafka.common.serialization.Serializer
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer

class AvroSerde(private val schema: Schema) : Serde<GenericRecord>, Deserializer<GenericRecord>,
    Serializer<GenericRecord> {

    private val schemaString = schema.toString()

    private var decoderFactory = DecoderFactory.get()
    private var encoderFactory = EncoderFactory.get()

    private lateinit var schemaRegistryClient: SchemaRegistryClient

    override fun serialize(topic: String, data: GenericRecord?): ByteArray? {
        if (data == null) {
            return null
        }

        val writer = GenericDatumWriter<GenericRecord>(schema, GenericDataWithLogicalTypes.get())
        val out = ByteArrayOutputStream()

        out.write(0) // magic byte
        val id = schemaRegistryClient.register(topic, SchemaType.AVRO, schemaString)
        out.write(ByteBuffer.allocate(4).putInt(id).array())

        val encoder = encoderFactory.binaryEncoder(out, null)
        writer.write(data, encoder)
        encoder.flush()

        val bytes = out.toByteArray()
        out.close()

        return bytes
    }

    // Cache for parsed writer schemas by ID (thread-safe for concurrent Kafka Streams access)
    private val writerSchemaCache = java.util.concurrent.ConcurrentHashMap<Int, Schema>()

    override fun deserialize(topic: String?, data: ByteArray?): GenericRecord? {
        if (data == null) {
            return null
        }

        val buffer = ByteBuffer.wrap(data)

        buffer.get() // skip magic byte
        val schemaId = buffer.int // get schema id

        // Look up writer's schema from registry (with caching)
        val writerSchema = writerSchemaCache.getOrPut(schemaId) {
            val schemaString = schemaRegistryClient.schema(schemaId)
            Schema.Parser().parse(schemaString)
        }

        // Use writer schema for deserialization, reader schema for projection/evolution
        val reader = GenericDatumReader<GenericRecord>(writerSchema, writerSchema, GenericDataWithLogicalTypes.get())

        val length = (buffer.limit() - 1) - 4 // take magic byte into account
        val start = buffer.position() + buffer.arrayOffset()

        val out = reader.read(null, decoderFactory.binaryDecoder(buffer.array(), start, length, null))

        return out
    }

    override fun serializer(): Serializer<GenericRecord> = this
    override fun deserializer(): Deserializer<GenericRecord> = this

    override fun close() {}
    override fun configure(configs: MutableMap<String, *>, isKey: Boolean) {
        schemaRegistryClient = SchemaRegistryClient(SchemaRegistryConfig.fromMap(configs))
    }
}
