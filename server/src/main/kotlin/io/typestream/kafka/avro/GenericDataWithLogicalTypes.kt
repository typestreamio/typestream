package io.typestream.kafka.avro

import org.apache.avro.Conversions
import org.apache.avro.data.TimeConversions
import org.apache.avro.generic.GenericData


object GenericDataWithLogicalTypes {
    private val genericData = GenericData.get()

    init {
        genericData.addLogicalTypeConversion(Conversions.UUIDConversion())
        genericData.addLogicalTypeConversion(Conversions.DecimalConversion())

        genericData.addLogicalTypeConversion(TimeConversions.DateConversion())
        genericData.addLogicalTypeConversion(TimeConversions.TimeMicrosConversion())
        genericData.addLogicalTypeConversion(TimeConversions.TimestampMillisConversion())
        genericData.addLogicalTypeConversion(TimeConversions.TimestampMicrosConversion())
        genericData.addLogicalTypeConversion(TimeConversions.TimestampMillisConversion())
    }

    fun get(): GenericData = genericData
}
