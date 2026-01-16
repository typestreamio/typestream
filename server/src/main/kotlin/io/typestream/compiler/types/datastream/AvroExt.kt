package io.typestream.compiler.types.datastream

import io.typestream.compiler.types.DataStream
import io.typestream.compiler.types.schema.Schema
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import org.apache.avro.LogicalTypes
import org.apache.avro.LogicalTypes.Decimal
import org.apache.avro.generic.GenericData
import org.apache.avro.generic.GenericRecord
import java.util.concurrent.atomic.AtomicInteger
import org.apache.avro.Schema as AvroSchema

// Counter for generating unique nested record names within a schema generation
private val nestedRecordCounter = ThreadLocal.withInitial { AtomicInteger(0) }

fun DataStream.Companion.fromAvroGenericRecord(path: String, genericRecord: GenericRecord): DataStream {
    val values = genericRecord.schema.fields.map { avroField ->
        avroField.toSchemaField(genericRecord)
    }

    // Preserve the original Avro schema for pass-through scenarios
    return DataStream(path, Schema.Struct(values), originalAvroSchema = genericRecord.schema.toString())
}

fun DataStream.Companion.fromAvroSchema(path: String, avroSchema: AvroSchema): DataStream {
    // When parsing schema only (no data), use schemaOnly=true to create zero values for all types
    val values = avroSchema.fields.map { it.toSchemaField(value = null, schemaOnly = true) }

    // Preserve the original Avro schema for pass-through scenarios
    return DataStream(path, Schema.Struct(values), originalAvroSchema = avroSchema.toString())
}

fun DataStream.toAvroSchema(): AvroSchema {
    val parser = AvroSchema.Parser()

    // If we have the original Avro schema (e.g., from Debezium), use it directly
    // This preserves exact schema structure for pass-through scenarios
    if (originalAvroSchema != null) {
        return parser.parse(originalAvroSchema)
    }

    require(schema is Schema.Struct) { "top level value must be a struct" }

    // Reset the counter for this schema generation
    nestedRecordCounter.get().set(0)

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
        is Schema.Optional -> {
            // Use the actual inner type if available, otherwise fallback to string
            val innerType = schema.value?.let { toAvroType(it) } ?: """"string""""
            """["null",$innerType], "default": null"""
        }

        is Schema.Struct -> {
            val fields = schema.value.joinToString(",") { field ->
                """{"name": "${field.name}","type": ${toAvroType(field.value)}}"""
            }
            // Generate a unique name for nested records to avoid conflicts
            val recordIndex = nestedRecordCounter.get().getAndIncrement()
            val baseName = schema.value.joinToString("_") { it.name }

            """
                {
                    "type": "record",
                    "name": "${baseName}_$recordIndex",
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

/**
 * Convert an Avro field to a Schema.Field.
 * @param value The actual data value (null if not available)
 * @param schemaOnly If true, create zero values for Optional types instead of null
 *                   (used when parsing schema without data, e.g., for type inference)
 */
private fun AvroSchema.Field.toSchemaField(value: Any? = null, schemaOnly: Boolean = false): Schema.Field {
    return when (schema().type) {
        AvroSchema.Type.ARRAY -> {
            val elementType = schema().elementType
            val avroField = AvroSchema.Field(name(), elementType)
            val schemaType = avroField.toSchemaField(schemaOnly = schemaOnly).value

            if (value is List<*> && value.isNotEmpty()) {
                Schema.Field(name(), Schema.List(value.map { avroField.toSchemaField(it, schemaOnly).value }, schemaType))
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
            val schemaType = avroField.toSchemaField(schemaOnly = schemaOnly).value

            if (value is Map<*, *> && value.isNotEmpty()) {
                Schema.Field(
                    name(),
                    Schema.Map(
                        value.map { it.key.toString() to avroField.toSchemaField(it.value, schemaOnly).value }.toMap(),
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
                    it.toSchemaField(value.get(it.name()), schemaOnly)
                } else {
                    it.toSchemaField(schemaOnly = schemaOnly)
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
                // If value is null and we're not in schemaOnly mode, create Optional(null)
                // This preserves actual null values for Debezium before/after fields
                // In schemaOnly mode, create zero value for the inner type
                if (value == null && !schemaOnly) {
                    Schema.Field(name(), Schema.Optional(null))
                } else {
                    val optionalType = AvroSchema.Field(name(), nonNullType, null).toSchemaField(value, schemaOnly)
                    Schema.Field(name(), Schema.Optional(optionalType.value))
                }
            } else {
                error("Unsupported type: ${schema().type}")
            }
        }

        else -> error("Unsupported type: ${schema().type}")
    }
}

private fun AvroSchema.Field.toSchemaField(genericRecord: GenericRecord): Schema.Field {
    // When parsing from actual data, schemaOnly = false to preserve actual null values
    if (schema().type == AvroSchema.Type.RECORD) {
        return toSchemaField(genericRecord.get(pos()), schemaOnly = false)
    }
    return toSchemaField(genericRecord[name()], schemaOnly = false)
}

fun DataStream.toAvroGenericRecord(): GenericRecord {
    val avroSchema = toAvroSchema()
    val genericRecord = GenericData.Record(avroSchema)

    //TODO we shouldn't assume top level value is a struct
    require(schema is Schema.Struct) { "Top level value must be a struct" }

    schema.value.forEach { field ->
        val fieldAvroSchema = avroSchema.getField(field.name)?.schema()
        genericRecord.put(field.name, field.value.toAvroValue(fieldAvroSchema))
    }

    return genericRecord
}

/**
 * Extract the non-null type from a union schema ["null", T] or [T, "null"].
 * Returns the schema itself if it's not a union.
 */
private fun extractNonNullFromUnion(schema: AvroSchema): AvroSchema? {
    if (!schema.isUnion) return schema
    return schema.types.firstOrNull { it.type != AvroSchema.Type.NULL }
}

/**
 * Convert a Schema value to an Avro-compatible value.
 * Handles temporal types that need conversion from kotlinx.datetime to Long/Int.
 *
 * @param avroSchema Optional Avro schema to use for nested records. When provided (pass-through
 *                   scenarios with original Debezium schema), nested records will use the exact
 *                   schema from the original. When null, schemas are reconstructed (transformation
 *                   scenarios like join, geoip).
 */
private fun Schema.toAvroValue(avroSchema: AvroSchema? = null): Any? {
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
            // Use original schema if available (pass-through), otherwise reconstruct
            val nestedSchema = avroSchema ?: this.toAvroSchema()
            val nestedRecord = GenericData.Record(nestedSchema)
            value.forEach { field ->
                val fieldAvroSchema = nestedSchema.getField(field.name)?.schema()
                nestedRecord.put(field.name, field.value.toAvroValue(fieldAvroSchema))
            }
            nestedRecord
        }
        is Schema.List -> {
            val elementSchema = avroSchema?.elementType
            value.map { it.toAvroValue(elementSchema) }
        }
        is Schema.Map -> {
            val valueSchema = avroSchema?.valueType
            value.mapValues { it.value.toAvroValue(valueSchema) }
        }
        is Schema.Optional -> {
            // For unions, get the non-null type for the nested value
            val innerSchema = avroSchema?.let { extractNonNullFromUnion(it) }
            value?.toAvroValue(innerSchema)
        }
        else -> value
    }
}

private fun Schema.Struct.toAvroSchema(): AvroSchema {
    val parser = AvroSchema.Parser()
    val fields = value.joinToString(",") { field ->
        """{"name": "${field.name}","type": ${toAvroType(field.value)}}"""
    }
    // Generate a unique name for nested records to avoid conflicts
    val recordIndex = nestedRecordCounter.get().getAndIncrement()
    val baseName = value.joinToString("_") { it.name }
    val schemaDefinition = """
        {
            "type": "record",
            "name": "${baseName}_$recordIndex",
            "namespace": "io.typestream.avro",
            "fields": [${fields}]
        }
    """.trimIndent()
    return parser.parse(schemaDefinition)
}
