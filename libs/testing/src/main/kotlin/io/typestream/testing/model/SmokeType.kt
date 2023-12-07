package io.typestream.testing.model

import io.typestream.testing.avro.Color
import io.typestream.testing.avro.NestedRecord
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID
import io.typestream.testing.avro.SmokeType as AvroSmokeType

data class SmokeType(override val id: String = "42") : TestRecord {
    override fun toAvro(): AvroSmokeType {
        val instant = Instant.ofEpochMilli(1576226562000L)
        val localDate = LocalDate.ofInstant(instant, ZoneId.systemDefault())
        val localTime = LocalTime.ofInstant(instant, ZoneId.systemDefault())
        val localDateTime = localTime.atDate(localDate)

        return AvroSmokeType.newBuilder()
            .setBooleanField(true)
            .setDoubleField(1.0)
            .setFloatField(2.0f)
            .setIntField(3)
            .setLongField(4L)
            .setStringField("5")
            .setArrayField(listOf("a", "b", "c"))
            .setEnumField(Color.RED)
            .setMapField(mapOf("key" to "value"))
            .setRecordField(NestedRecord.newBuilder().setNestedInt(11).setNestedString("12").build())
            .setDateField(localDate)
            .setDecimalField("13.00".toBigDecimal())
            .setLocalTimestampMicrosField(localDateTime)
            .setLocalTimestampMillisField(localDateTime)
            .setTimeMicrosField(localTime)
            .setTimeMillisField(localTime)
            .setTimestampMicrosField(instant)
            .setTimestampMillisField(instant)
            .setUuidField(UUID.fromString("2F5C4556-B5A1-45CE-AB36-DA41AEFF7E8D"))
            .setOptionalField("24")
            .build()
    }

    override fun toProto() = TODO()
}
