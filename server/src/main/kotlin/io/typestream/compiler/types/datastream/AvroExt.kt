package io.typestream.compiler.types.datastream

import io.typestream.compiler.types.DataStream
import io.typestream.compiler.types.schema.Schema
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import org.apache.avro.LogicalTypes
import org.apache.avro.LogicalTypes.Decimal
import org.apache.avro.generic.GenericData
import org.apache.avro.generic.GenericRecord
import org.apache.avro.Schema as AvroSchema

fun DataStream.Companion.fromAvroGenericRecord(path: String, genericRecord: GenericRecord): DataStream {
    val values = genericRecord.schema.fields.map { avroField ->
        avroField.toSchemaField(genericRecord)
    }

    return DataStream(path, Schema.Struct(values))
}

fun DataStream.Companion.fromAvroSchema(path: String, avroSchema: AvroSchema): DataStream {
    val values = avroSchema.fields.map(AvroSchema.Field::toSchemaField)

    return DataStream(path, Schema.Struct(values))
}

fun DataStream.toAvroSchema(): AvroSchema {
    val parser = AvroSchema.Parser()

    require(schema is Schema.Struct) { "top level value must be a struct" }

    val fields = schema.value.joinToString(",") { field ->
        """{"name": "${field.name}","type": ${toAvroType(field.value)}}""".trimIndent()
    }

    val schemaDefinition = """
        {
            "type": "record",
            "name": "${path.replace("/", "_").replace("-", "_")}",
            "namespace": "io.typestream.avro",
            "fields": [${fields}]
        }
        """.trimIndent()

    return parser.parse(schemaDefinition)
}

private fun toAvroType(schema: Schema): String {
    return when (schema) {
        is Schema.Boolean -> """"boolean""""
        is Schema.Date -> """{"type":"int","logicalType":"date"}"""
        is Schema.DateTime -> when (schema.precision) {
            Schema.DateTime.Precision.MILLIS -> """{"type":"long","logicalType":"local-timestamp-millis"}"""
            Schema.DateTime.Precision.MICROS -> """{"type":"long","logicalType":"local-timestamp-micros"}"""
        }

        is Schema.Double -> """"double""""
        is Schema.Enum -> """{"type":"enum","name": "${schema.symbols.joinToString("_")}","symbols":[${
            schema.symbols.joinToString(",") { "\"$it\"" }
        }]}""".trimIndent()

        is Schema.Float -> """"float""""
        is Schema.Decimal -> """{"type":"bytes","logicalType":"decimal","precision":9,"scale":2}"""
        is Schema.Instant -> when (schema.precision) {
            Schema.Instant.Precision.MILLIS -> """{"type":"long","logicalType":"timestamp-millis"}"""
            Schema.Instant.Precision.MICROS -> """{"type":"long","logicalType":"timestamp-micros"}"""
        }
        is Schema.Int -> """"int""""
        is Schema.UUID -> """{"type":"string","logicalType":"uuid"}"""
        is Schema.String -> """"string""""
        is Schema.Long -> """"long""""
        is Schema.List -> """{"type":"array","items": ${toAvroType(schema.valueType)}}"""
        is Schema.Map -> """{"type":"map","values":${toAvroType(schema.valueType)}}"""
        is Schema.Optional -> """["null","string"], "default": null"""

        is Schema.Struct -> {
            val fields = schema.value.joinToString(",") { field ->
                """{"name": "${field.name}","type": ${toAvroType(field.value)}}"""
            }

            """
                {
                    "type": "record",
                    "name": "${schema.value.joinToString("_") { it.name }}",
                    "fields": [${fields}]
                }
            """.trimIndent()
        }

        is Schema.Time -> when (schema.precision) {
            Schema.Time.Precision.MILLIS -> """{"type":"int","logicalType":"time-millis"}"""
            Schema.Time.Precision.MICROS -> """{"type":"long","logicalType":"time-micros"}"""
        }
    }
}

private fun AvroSchema.Field.toSchemaField(value: Any? = null): Schema.Field {
    return when (schema().type) {
        AvroSchema.Type.ARRAY -> {
            val elementType = schema().elementType
            val avroField = AvroSchema.Field(name(), elementType)
            val schemaType = avroField.toSchemaField().value

            if (value is List<*> && value.isNotEmpty()) {
                Schema.Field(name(), Schema.List(value.map { avroField.toSchemaField(it).value }, schemaType))
            } else {
                Schema.Field(name(), Schema.List(listOf(), schemaType))
            }
        }

        AvroSchema.Type.BOOLEAN -> Schema.Field(name(), Schema.Boolean.fromAnyValue(value))

        AvroSchema.Type.BYTES -> when (schema().logicalType) {
            is Decimal -> Schema.Field(name(), Schema.Decimal.fromAnyValue(value))
            else -> error("Unsupported type: ${schema().type}")
        }

        AvroSchema.Type.DOUBLE -> Schema.Field(name(), Schema.Double.fromAnyValue(value))
        AvroSchema.Type.ENUM -> if (value != null) {
            Schema.Field(name(), Schema.Enum(value.toString(), schema().enumSymbols))
        } else {
            Schema.Field(name(), Schema.Enum("", schema().enumSymbols))
        }

        AvroSchema.Type.FIXED -> when (schema().logicalType) {
            is Decimal -> Schema.Field(name(), Schema.Decimal.fromAnyValue(value))
            else -> error("Unsupported type: ${schema().type}")
        }

        AvroSchema.Type.FLOAT -> Schema.Field(name(), Schema.Float.fromAnyValue(value))
        AvroSchema.Type.INT -> when (schema().logicalType) {
            is LogicalTypes.Date -> Schema.Field(name(), Schema.Date.fromAnyValue(value))
            is LogicalTypes.TimeMillis -> Schema.Field(
                name(),
                Schema.Time.fromAnyValue(value, Schema.Time.Precision.MILLIS)
            )

            else -> Schema.Field(name(), Schema.Int.fromAnyValue(value))
        }


        AvroSchema.Type.LONG -> when (schema().logicalType) {
            is LogicalTypes.LocalTimestampMillis -> Schema.Field(
                name(),
                Schema.DateTime.fromAnyValue(value, Schema.DateTime.Precision.MILLIS)
            )

            is LogicalTypes.LocalTimestampMicros -> Schema.Field(
                name(),
                Schema.DateTime.fromAnyValue(value, Schema.DateTime.Precision.MICROS)
            )

            is LogicalTypes.TimeMicros -> Schema.Field(
                name(),
                Schema.Time.fromAnyValue(value, Schema.Time.Precision.MICROS)
            )

            is LogicalTypes.TimestampMillis -> Schema.Field(
                name(),
                Schema.Instant.fromAnyValue(value, Schema.Instant.Precision.MILLIS)
            )

            is LogicalTypes.TimestampMicros -> Schema.Field(
                name(),
                Schema.Instant.fromAnyValue(value, Schema.Instant.Precision.MICROS)
            )

            else -> Schema.Field(name(), Schema.Long.fromAnyValue(value))
        }


        AvroSchema.Type.MAP -> {
            val valueType = schema().valueType
            val avroField = AvroSchema.Field(name(), valueType)
            val schemaType = avroField.toSchemaField().value

            if (value is Map<*, *> && value.isNotEmpty()) {
                Schema.Field(
                    name(),
                    Schema.Map(
                        value.map { it.key.toString() to avroField.toSchemaField(it.value).value }.toMap(),
                        schemaType
                    )
                )
            } else {
                Schema.Field(name(), Schema.Map(mapOf(), schemaType))
            }
        }

        AvroSchema.Type.RECORD -> {
            val fields = schema().fields.map {
                if (value is GenericRecord) {
                    it.toSchemaField(value.get(it.name()))
                } else {
                    it.toSchemaField()
                }
            }
            Schema.Field(name(), Schema.Struct(fields))
        }

        AvroSchema.Type.STRING -> when (schema().logicalType) {
            LogicalTypes.uuid() -> Schema.Field(name(), Schema.UUID.fromAnyValue(value))
            else -> Schema.Field(name(), Schema.String.fromAnyValue(value))
        }

        AvroSchema.Type.UNION -> {
            val types = schema().types
            if (types.size == 2 && types.count { it.type == AvroSchema.Type.NULL } == 1) {
                val nonNullType = types.first { it.type != AvroSchema.Type.NULL }
                val optionalType = AvroSchema.Field(name(), nonNullType, null).toSchemaField(value)

                Schema.Field(name(), Schema.Optional(optionalType.value))
            } else {
                error("Unsupported type: ${schema().type}")
            }
        }

        else -> error("Unsupported type: ${schema().type}")
    }
}

private fun AvroSchema.Field.toSchemaField(genericRecord: GenericRecord): Schema.Field {
    if (schema().type == AvroSchema.Type.RECORD) {
        return toSchemaField(genericRecord.get(pos()))
    }
    return toSchemaField(genericRecord[name()])
}

fun DataStream.toAvroGenericRecord(): GenericRecord {
    val genericRecord = GenericData.Record(toAvroSchema())

    //TODO we shouldn't assume top level value is a struct
    require(schema is Schema.Struct) { "Top level value must be a struct" }

    schema.value.forEach { field -> genericRecord.put(field.name, field.value.toAvroValue()) }

    return genericRecord
}

/**
 * Convert a Schema value to an Avro-compatible value.
 * Handles temporal types that need conversion from kotlinx.datetime to Long/Int.
 */
private fun Schema.toAvroValue(): Any? {
    return when (this) {
        is Schema.Instant -> when (precision) {
            Schema.Instant.Precision.MILLIS -> value.toEpochMilliseconds()
            Schema.Instant.Precision.MICROS -> value.toEpochMilliseconds() * 1000 + (value.nanosecondsOfSecond / 1000) % 1000
        }
        is Schema.DateTime -> {
            val instant = value.toInstant(kotlinx.datetime.TimeZone.UTC)
            when (precision) {
                Schema.DateTime.Precision.MILLIS -> instant.toEpochMilliseconds()
                Schema.DateTime.Precision.MICROS -> instant.toEpochMilliseconds() * 1000 + (instant.nanosecondsOfSecond / 1000) % 1000
            }
        }
        is Schema.Date -> value.toEpochDays()
        is Schema.Time -> when (precision) {
            Schema.Time.Precision.MILLIS -> value.toMillisecondOfDay()
            Schema.Time.Precision.MICROS -> value.toMillisecondOfDay() * 1000L + (value.nanosecond / 1000) % 1000
        }
        is Schema.Struct -> {
            val nestedSchema = this.toAvroSchema()
            val nestedRecord = GenericData.Record(nestedSchema)
            value.forEach { field -> nestedRecord.put(field.name, field.value.toAvroValue()) }
            nestedRecord
        }
        is Schema.List -> value.map { it.toAvroValue() }
        is Schema.Map -> value.mapValues { it.value.toAvroValue() }
        is Schema.Optional -> value?.toAvroValue()
        else -> value
    }
}

private fun Schema.Struct.toAvroSchema(): AvroSchema {
    val parser = AvroSchema.Parser()
    val fields = value.joinToString(",") { field ->
        """{"name": "${field.name}","type": ${toAvroType(field.value)}}"""
    }
    val schemaDefinition = """
        {
            "type": "record",
            "name": "${value.joinToString("_") { it.name }}",
            "namespace": "io.typestream.avro",
            "fields": [${fields}]
        }
    """.trimIndent()
    return parser.parse(schemaDefinition)
}
