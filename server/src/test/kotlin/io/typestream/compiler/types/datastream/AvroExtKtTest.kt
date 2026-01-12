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
        assertThat(avroRecord.get("dateField")).isInstanceOf(java.lang.Integer::class.java)
        assertThat(avroRecord.get("timestampMillisField")).isInstanceOf(java.lang.Long::class.java)
        assertThat(avroRecord.get("timestampMicrosField")).isInstanceOf(java.lang.Long::class.java)
        assertThat(avroRecord.get("localTimestampMillisField")).isInstanceOf(java.lang.Long::class.java)
        assertThat(avroRecord.get("localTimestampMicrosField")).isInstanceOf(java.lang.Long::class.java)
        assertThat(avroRecord.get("timeMillisField")).isInstanceOf(java.lang.Integer::class.java)
        assertThat(avroRecord.get("timeMicrosField")).isInstanceOf(java.lang.Long::class.java)
    }
}
