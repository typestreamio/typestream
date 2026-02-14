package io.typestream.compiler.types.datastream

import io.typestream.compiler.types.DataStream
import io.typestream.compiler.types.schema.Schema
import io.typestream.config.SchemaRegistryConfig
import io.typestream.kafka.schemaregistry.SchemaRegistryClient
import io.typestream.testing.TestKafkaContainer
import org.apache.avro.SchemaBuilder
import org.apache.avro.generic.GenericData
import org.apache.avro.generic.GenericDatumWriter
import org.apache.avro.io.EncoderFactory
import org.apache.kafka.common.utils.Bytes
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.testcontainers.junit.jupiter.Testcontainers
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import org.apache.avro.Schema as AvroSchema

@Testcontainers
internal class BytesExtTest {

    companion object {
        private val testKafka = TestKafkaContainer.instance
        private val schemaRegistryClient = SchemaRegistryClient(SchemaRegistryConfig(testKafka.schemaRegistryAddress))
    }

    /**
     * Register a schema and encode a value in Confluent Avro wire format:
     * magic byte (0x00) + 4-byte schema ID + Avro binary data
     */
    private fun avroWireFormat(subject: String, schema: AvroSchema, encode: (org.apache.avro.io.Encoder) -> Unit): Bytes {
        val schemaId = schemaRegistryClient.register(subject, io.typestream.kafka.schemaregistry.SchemaType.AVRO, schema.toString())

        val baos = ByteArrayOutputStream()
        baos.write(0x00) // magic byte
        baos.write(ByteBuffer.allocate(4).putInt(schemaId).array())

        val encoder = EncoderFactory.get().binaryEncoder(baos, null)
        encode(encoder)
        encoder.flush()

        return Bytes(baos.toByteArray())
    }

    private fun avroRecordWireFormat(subject: String, schema: AvroSchema, record: GenericData.Record): Bytes {
        val schemaId = schemaRegistryClient.register(subject, io.typestream.kafka.schemaregistry.SchemaType.AVRO, schema.toString())

        val baos = ByteArrayOutputStream()
        baos.write(0x00)
        baos.write(ByteBuffer.allocate(4).putInt(schemaId).array())

        val writer = GenericDatumWriter<GenericData.Record>(schema)
        val encoder = EncoderFactory.get().binaryEncoder(baos, null)
        writer.write(record, encoder)
        encoder.flush()

        return Bytes(baos.toByteArray())
    }

    @Test
    fun `fromKeyBytes decodes Avro int key`() {
        val subject = "key-test-int-${System.nanoTime()}-key"
        val bytes = avroWireFormat(subject, AvroSchema.create(AvroSchema.Type.INT)) { it.writeInt(42) }

        val ds = DataStream.fromKeyBytes("test", bytes, schemaRegistryClient)

        assertThat(ds.schema).isEqualTo(Schema.Int(42))
    }

    @Test
    fun `fromKeyBytes decodes Avro long key`() {
        val subject = "key-test-long-${System.nanoTime()}-key"
        val bytes = avroWireFormat(subject, AvroSchema.create(AvroSchema.Type.LONG)) { it.writeLong(123L) }

        val ds = DataStream.fromKeyBytes("test", bytes, schemaRegistryClient)

        assertThat(ds.schema).isEqualTo(Schema.Long(123L))
    }

    @Test
    fun `fromKeyBytes decodes Avro string key`() {
        val subject = "key-test-string-${System.nanoTime()}-key"
        val bytes = avroWireFormat(subject, AvroSchema.create(AvroSchema.Type.STRING)) { it.writeString("user-42") }

        val ds = DataStream.fromKeyBytes("test", bytes, schemaRegistryClient)

        assertThat(ds.schema).isEqualTo(Schema.String("user-42"))
    }

    @Test
    fun `fromKeyBytes flattens single-field record key`() {
        val subject = "key-test-record-${System.nanoTime()}-key"

        val keySchema = SchemaBuilder.record("Key")
            .namespace("test")
            .fields()
            .requiredInt("id")
            .endRecord()

        val record = GenericData.Record(keySchema)
        record.put("id", 7)

        val bytes = avroRecordWireFormat(subject, keySchema, record)
        val ds = DataStream.fromKeyBytes("test", bytes, schemaRegistryClient)

        assertThat(ds.schema).isEqualTo(Schema.Int(7))
    }

    @Test
    fun `fromKeyBytes preserves multi-field record key as struct`() {
        val subject = "key-test-composite-${System.nanoTime()}-key"

        val keySchema = SchemaBuilder.record("CompositeKey")
            .namespace("test")
            .fields()
            .requiredInt("region")
            .requiredString("id")
            .endRecord()

        val record = GenericData.Record(keySchema)
        record.put("region", 1)
        record.put("id", "abc")

        val bytes = avroRecordWireFormat(subject, keySchema, record)
        val ds = DataStream.fromKeyBytes("test", bytes, schemaRegistryClient)

        assertThat(ds.schema).isInstanceOf(Schema.Struct::class.java)
        val struct = ds.schema as Schema.Struct
        assertThat(struct.value).containsExactly(
            Schema.Field("region", Schema.Int(1)),
            Schema.Field("id", Schema.String("abc")),
        )
    }

    @Test
    fun `fromKeyBytes falls back to string for non-Avro bytes`() {
        val raw = "plain-key".toByteArray()
        val ds = DataStream.fromKeyBytes("test", Bytes(raw), schemaRegistryClient)

        assertThat(ds.schema).isEqualTo(Schema.String("plain-key"))
    }

    @Test
    fun `fromKeyBytes falls back to string when schema registry is null`() {
        val ds = DataStream.fromKeyBytes("test", Bytes("some-key".toByteArray()), null)

        assertThat(ds.schema).isEqualTo(Schema.String("some-key"))
    }
}
