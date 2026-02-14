package io.typestream.compiler.types.datastream

import io.typestream.compiler.types.DataStream
import io.typestream.compiler.types.schema.Schema
import io.typestream.kafka.schemaregistry.SchemaRegistryClient
import org.apache.avro.generic.GenericDatumReader
import org.apache.avro.generic.GenericRecord
import org.apache.avro.io.DecoderFactory
import org.apache.kafka.common.utils.Bytes
import java.nio.ByteBuffer
import java.util.concurrent.ConcurrentHashMap
import org.apache.avro.Schema as AvroSchema

fun DataStream.Companion.fromBytes(path: String, value: Bytes) = DataStream(path, Schema.String(value.toString()))

fun DataStream.toBytes(): Bytes = Bytes.wrap(schema.value.toString().toByteArray())

private val keySchemaCache = ConcurrentHashMap<Int, AvroSchema>()

private fun avroFieldToDataStream(path: String, fieldSchema: AvroSchema, value: Any?): DataStream {
    return when (fieldSchema.type) {
        AvroSchema.Type.INT -> DataStream(path, Schema.Int(value as Int))
        AvroSchema.Type.LONG -> DataStream(path, Schema.Long(value as Long))
        AvroSchema.Type.STRING -> DataStream(path, Schema.String(value.toString()))
        AvroSchema.Type.FLOAT -> DataStream(path, Schema.Float(value as Float))
        AvroSchema.Type.DOUBLE -> DataStream(path, Schema.Double(value as Double))
        AvroSchema.Type.BOOLEAN -> DataStream(path, Schema.Boolean(value as Boolean))
        else -> DataStream(path, Schema.String(value.toString()))
    }
}

fun DataStream.Companion.fromKeyBytes(path: String, value: Bytes, schemaRegistryClient: SchemaRegistryClient?): DataStream {
    val raw = value.get()
    // Check for Avro wire format: magic byte 0x00 + 4-byte schema ID
    if (schemaRegistryClient == null || raw.size < 5 || raw[0] != 0.toByte()) {
        return fromBytes(path, value)
    }

    val buffer = ByteBuffer.wrap(raw)
    buffer.get() // skip magic byte
    val schemaId = buffer.int

    val avroSchema = keySchemaCache.getOrPut(schemaId) {
        AvroSchema.Parser().parse(schemaRegistryClient.schema(schemaId))
    }

    val start = buffer.position() + buffer.arrayOffset()
    val length = buffer.remaining()
    val decoder = DecoderFactory.get().binaryDecoder(raw, start, length, null)

    return when (avroSchema.type) {
        AvroSchema.Type.INT -> DataStream(path, Schema.Int(decoder.readInt()))
        AvroSchema.Type.LONG -> DataStream(path, Schema.Long(decoder.readLong()))
        AvroSchema.Type.STRING -> DataStream(path, Schema.String(decoder.readString()))
        AvroSchema.Type.FLOAT -> DataStream(path, Schema.Float(decoder.readFloat()))
        AvroSchema.Type.DOUBLE -> DataStream(path, Schema.Double(decoder.readDouble()))
        AvroSchema.Type.BOOLEAN -> DataStream(path, Schema.Boolean(decoder.readBoolean()))
        AvroSchema.Type.RECORD -> {
            val reader = GenericDatumReader<GenericRecord>(avroSchema)
            val record = reader.read(null, decoder)
            // Flatten single-field key records (e.g. Debezium {"id": 1} -> 1)
            if (avroSchema.fields.size == 1) {
                val field = avroSchema.fields[0]
                val fieldValue = record.get(0)
                avroFieldToDataStream(path, field.schema(), fieldValue)
            } else {
                DataStream.fromAvroGenericRecord(path, record)
            }
        }
        else -> fromBytes(path, value)
    }
}
