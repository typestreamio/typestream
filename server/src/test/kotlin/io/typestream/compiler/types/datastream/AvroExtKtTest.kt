package io.typestream.compiler.types.datastream

import io.typestream.compiler.types.DataStream
import io.typestream.compiler.types.schema.Schema
import io.typestream.testing.avro.NestedRecord
import io.typestream.testing.model.SmokeType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import io.typestream.testing.avro.SmokeType as AvroSmokeType

internal class AvroExtKtTest {
    @Test
    fun fromAvroSchema() {
        val schema = DataStream.fromAvroSchema("smokeType", AvroSmokeType.getClassSchema()).schema
        require(schema is Schema.Struct)

        assertThat(schema.value).containsExactly(
            Schema.Field("booleanField", Schema.Boolean(false)),
            Schema.Field("doubleField", Schema.Double(0.0)),
            Schema.Field("floatField", Schema.Float(0.0f)),
            Schema.Field("intField", Schema.Int(0)),
            Schema.Field("longField", Schema.Long(0L)),
            Schema.Field("stringField", Schema.String("")),
            Schema.Field("arrayField", Schema.List(listOf(), Schema.String.zeroValue)),
            Schema.Field("enumField", Schema.Enum("", listOf("RED", "GREEN", "BLUE"))),
            Schema.Field("mapField", Schema.Map(mapOf(), Schema.String.zeroValue)),
            Schema.Field(
                "recordField", Schema.Struct(
                    listOf(
                        Schema.Field("nestedInt", Schema.Int(0)),
                        Schema.Field("nestedString", Schema.String.zeroValue),
                    )
                )
            ),
            Schema.Field("dateField", Schema.Date(kotlinx.datetime.LocalDate.fromEpochDays(0))),
            Schema.Field("decimalField", Schema.Decimal(BigDecimal.ZERO)),
            Schema.Field("localTimestampMicrosField", Schema.DateTime.zeroValue(Schema.DateTime.Precision.MICROS)),
            Schema.Field("localTimestampMillisField", Schema.DateTime.zeroValue(Schema.DateTime.Precision.MILLIS)),
            Schema.Field("timeMicrosField", Schema.Time.zeroValue(Schema.Time.Precision.MICROS)),
            Schema.Field("timeMillisField", Schema.Time.zeroValue(Schema.Time.Precision.MILLIS)),
            Schema.Field("timestampMicrosField", Schema.Instant.zeroValue(Schema.Instant.Precision.MICROS)),
            Schema.Field("timestampMillisField", Schema.Instant.zeroValue(Schema.Instant.Precision.MILLIS)),
            Schema.Field("uuidField", Schema.UUID.zeroValue),
            Schema.Field("optionalField", Schema.Optional(Schema.String.zeroValue)),
        )
    }

    @Test
    fun fromGenericRecord() {
        val smokeType = SmokeType().toAvro()
        val dataStream = DataStream.fromAvroGenericRecord("smokeType", smokeType)

        assertThat(dataStream["booleanField"].value).isEqualTo(smokeType.get("booleanField"))
        assertThat(dataStream["doubleField"].value).isEqualTo(smokeType.get("doubleField"))
        assertThat(dataStream["floatField"].value).isEqualTo(smokeType.get("floatField"))
        assertThat(dataStream["intField"].value).isEqualTo(smokeType.get("intField"))
        assertThat(dataStream["longField"].value).isEqualTo(smokeType.get("longField"))
        assertThat(dataStream["stringField"].value).isEqualTo(smokeType.get("stringField"))

        val arrayField = dataStream["arrayField"]
        require(arrayField is Schema.List)
        assertThat(arrayField.value.map { it.value }).isEqualTo(smokeType.get("arrayField"))

        assertThat(dataStream["enumField"].value).isEqualTo(smokeType.get("enumField").toString())

        val mapField = dataStream["mapField"]
        require(mapField is Schema.Map)
        assertThat(mapField.value.map { it.key to it.value.value }.toMap()).isEqualTo(smokeType.get("mapField"))

        val nestedRecord = smokeType.get("recordField") as NestedRecord
        assertThat(dataStream["recordField.nestedInt"].value).isEqualTo(nestedRecord.nestedInt)
        assertThat(dataStream["recordField.nestedString"].value).isEqualTo(nestedRecord.nestedString)

        assertThat(dataStream["dateField"].value.toString()).isEqualTo(smokeType.get("dateField").toString())
        assertThat(dataStream["decimalField"].value).isEqualTo(smokeType.get("decimalField"))

        assertThat(dataStream["localTimestampMicrosField"].value.toString()).isEqualTo(
            smokeType.get("localTimestampMicrosField").toString()
        )
        assertThat(dataStream["localTimestampMillisField"].value.toString()).isEqualTo(
            smokeType.get("localTimestampMillisField").toString()
        )
        assertThat(dataStream["timeMicrosField"].value.toString()).isEqualTo(
            smokeType.get("timeMicrosField").toString()
        )
        assertThat(dataStream["timeMillisField"].value.toString()).isEqualTo(
            smokeType.get("timeMillisField").toString()
        )
        assertThat(dataStream["timestampMicrosField"].value.toString()).isEqualTo(
            smokeType.get("timestampMicrosField").toString()
        )
        assertThat(dataStream["timestampMillisField"].value.toString()).isEqualTo(
            smokeType.get("timestampMillisField").toString()
        )

        assertThat(dataStream["uuidField"].value).isEqualTo(smokeType.get("uuidField"))
        val optionalField = dataStream["optionalField"]
        require(optionalField is Schema.Optional)
        assertThat(optionalField.value?.value.toString()).isEqualTo(smokeType.get("optionalField"))
    }

    @Test
    fun toAvroGenericRecord() {
        val originalAvro = SmokeType().toAvro()
        val dataStream = DataStream.fromAvroGenericRecord("smokeType", originalAvro)
        val avroRecord = dataStream.toAvroGenericRecord()

        // Verify primitive fields
        assertThat(avroRecord.get("booleanField")).isEqualTo(true)
        assertThat(avroRecord.get("intField")).isEqualTo(3)
        assertThat(avroRecord.get("longField")).isEqualTo(4L)
        assertThat(avroRecord.get("stringField")).isEqualTo("5")

        // Verify temporal fields are converted to correct primitive types (the actual fix)
        assertThat(avroRecord.get("dateField")).isInstanceOf(Int::class.javaObjectType)
        assertThat(avroRecord.get("timestampMillisField")).isInstanceOf(Long::class.javaObjectType)
        assertThat(avroRecord.get("timestampMicrosField")).isInstanceOf(Long::class.javaObjectType)
        assertThat(avroRecord.get("localTimestampMillisField")).isInstanceOf(Long::class.javaObjectType)
        assertThat(avroRecord.get("localTimestampMicrosField")).isInstanceOf(Long::class.javaObjectType)
        assertThat(avroRecord.get("timeMillisField")).isInstanceOf(Int::class.javaObjectType)
        assertThat(avroRecord.get("timeMicrosField")).isInstanceOf(Long::class.javaObjectType)
    }

    @Test
    fun `toAvroSchema preserves Optional with nested struct type`() {
        // Create a DataStream with an optional struct field (like Debezium 'before' or 'after')
        val valueStruct = Schema.Struct(
            listOf(
                Schema.Field("id", Schema.Int(0)),
                Schema.Field("name", Schema.String("")),
            )
        )

        val recordSchema = Schema.Struct(
            listOf(
                Schema.Field("payload", Schema.Optional(valueStruct)),
                Schema.Field("op", Schema.String("")),
            )
        )

        val dataStream = DataStream("test/record", recordSchema)
        val avroSchema = dataStream.toAvroSchema()

        // Verify the 'payload' field is a union of null and record (not null and string)
        val payloadField = avroSchema.getField("payload")
        assertThat(payloadField.schema().isUnion).isTrue()
        val payloadTypes = payloadField.schema().types
        assertThat(payloadTypes).hasSize(2)
        assertThat(payloadTypes[0].type).isEqualTo(org.apache.avro.Schema.Type.NULL)
        assertThat(payloadTypes[1].type).isEqualTo(org.apache.avro.Schema.Type.RECORD)
        assertThat(payloadTypes[1].fields.map { it.name() }).containsExactly("id", "name")
    }

    @Test
    fun `toAvroGenericRecord handles Optional struct with null value`() {
        // Create a DataStream like a Debezium INSERT where 'before' is null
        val valueStruct = Schema.Struct(
            listOf(
                Schema.Field("id", Schema.Int(42)),
                Schema.Field("name", Schema.String("test")),
            )
        )

        val envelopeSchema = Schema.Struct(
            listOf(
                Schema.Field("before", Schema.Optional(null)),  // null for INSERT
                Schema.Field("after", Schema.Optional(valueStruct)),
                Schema.Field("op", Schema.String("c")),
            )
        )

        val dataStream = DataStream("test/envelope", envelopeSchema)
        val avroRecord = dataStream.toAvroGenericRecord()

        // Verify 'before' is null
        assertThat(avroRecord.get("before")).isNull()

        // Verify 'after' contains the nested record
        val afterRecord = avroRecord.get("after") as org.apache.avro.generic.GenericRecord
        assertThat(afterRecord.get("id")).isEqualTo(42)
        assertThat(afterRecord.get("name")).isEqualTo("test")

        // Verify 'op' is correct
        assertThat(avroRecord.get("op")).isEqualTo("c")
    }

    @Test
    fun `toAvroGenericRecord uses original schema names for Debezium envelope`() {
        // Debezium-style schema with named nested record "Value" referenced by name in 'after'
        val debeziumSchemaJson = """
            {
                "type": "record",
                "name": "Envelope",
                "namespace": "dbserver.public.users",
                "fields": [
                    {"name": "before", "type": ["null", {
                        "type": "record",
                        "name": "Value",
                        "fields": [
                            {"name": "id", "type": "int"},
                            {"name": "name", "type": "string"}
                        ]
                    }], "default": null},
                    {"name": "after", "type": ["null", "Value"], "default": null},
                    {"name": "op", "type": "string"}
                ]
            }
        """.trimIndent()

        // Create DataStream with originalAvroSchema set (simulating Debezium pass-through)
        val dataStream = DataStream(
            path = "dbserver.public.users",
            schema = Schema.Struct(
                listOf(
                    Schema.Field("before", Schema.Optional(null)),
                    Schema.Field(
                        "after", Schema.Optional(
                            Schema.Struct(
                                listOf(
                                    Schema.Field("id", Schema.Int(42)),
                                    Schema.Field("name", Schema.String("Alice"))
                                )
                            )
                        )
                    ),
                    Schema.Field("op", Schema.String("c"))
                )
            ),
            originalAvroSchema = debeziumSchemaJson
        )

        // Serialize - this should NOT throw UnresolvedUnionException
        val genericRecord = dataStream.toAvroGenericRecord()

        // Verify the envelope record uses the correct schema
        assertThat(genericRecord.schema.name).isEqualTo("Envelope")
        assertThat(genericRecord.schema.namespace).isEqualTo("dbserver.public.users")

        // Verify the nested 'after' record has the correct schema name "Value"
        val afterRecord = genericRecord.get("after") as org.apache.avro.generic.GenericRecord
        assertThat(afterRecord.schema.name).isEqualTo("Value")
        assertThat(afterRecord.get("id")).isEqualTo(42)
        assertThat(afterRecord.get("name")).isEqualTo("Alice")
    }

    @Test
    fun `toAvroGenericRecord reconstructs schema when originalAvroSchema is null`() {
        // No originalAvroSchema - simulates post-transformation scenario
        val dataStream = DataStream(
            path = "transformed/topic",
            schema = Schema.Struct(
                listOf(
                    Schema.Field(
                        "nested", Schema.Struct(
                            listOf(
                                Schema.Field("value", Schema.String("test"))
                            )
                        )
                    )
                )
            )
            // originalAvroSchema = null (default)
        )

        val genericRecord = dataStream.toAvroGenericRecord()

        // Schema should be reconstructed with generated namespace
        assertThat(genericRecord.schema.namespace).isEqualTo("io.typestream.avro")

        // Nested record should also have reconstructed schema
        val nestedRecord = genericRecord.get("nested") as org.apache.avro.generic.GenericRecord
        assertThat(nestedRecord.schema.namespace).isEqualTo("io.typestream.avro")
    }
}
