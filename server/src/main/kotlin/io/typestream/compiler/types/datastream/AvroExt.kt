package io.typestream.compiler.types.datastream

import io.typestream.compiler.types.DataStream
import io.typestream.compiler.types.schema.Schema
import org.apache.avro.generic.GenericData
import org.apache.avro.generic.GenericRecord
import org.apache.avro.Schema as AvroSchema

fun DataStream.Companion.fromAvroGenericRecord(path: String, genericRecord: GenericRecord): DataStream {
    val values = genericRecord.schema.fields.map { avroField ->
        avroField.toNamedValue(genericRecord)
    }

    return DataStream(path, Schema.Struct(values))
}

fun DataStream.Companion.fromAvroSchema(path: String, avroSchema: AvroSchema): DataStream {
    val values = avroSchema.fields.map(AvroSchema.Field::toNamedValue)

    return DataStream(path, Schema.Struct(values))
}

fun DataStream.toAvroSchema(): AvroSchema {
    val parser = AvroSchema.Parser()

    //TODO we shouldn't assume top level value is a struct
    require(schema is Schema.Struct) { "top level value must be a struct" }

    val fields = schema.value.joinToString(",") { namedValue ->
        """
            {
                "name": "${namedValue.name}",
                "type": ${toAvroType(namedValue.value)}
            }
        """.trimIndent()
    }

    val schemaDefinition = """
        {
            "type": "record",
            "name": "${path.replace("/", "_")}",
            "namespace": "io.typestream.avro",
            "fields": [${fields}]
        }
        """.trimIndent()

    return parser.parse(schemaDefinition)
}

private fun toAvroType(schema: Schema): String {
    return when (schema) {
        is Schema.UUID -> "{\"type\":\"string\",\"logicalType\":\"uuid\"}"
        is Schema.String -> "{\"type\":\"string\",\"avro.java.string\":\"String\"}"
        is Schema.Long -> "{\"type\":\"long\"}"
        is Schema.Int -> "{\"type\":\"int\"}"
        else -> throw IllegalArgumentException("Unsupported type: ${schema::class.simpleName}")
    }
}

private fun AvroSchema.Field.toNamedValue(): Schema.Named {
    return when (schema().type) {
        AvroSchema.Type.STRING -> Schema.Named(name(), Schema.String.empty)
        AvroSchema.Type.INT -> Schema.Named(name(), Schema.Int(0))
        AvroSchema.Type.LONG -> Schema.Named(name(), Schema.Long(0L))
        else -> throw IllegalArgumentException("Unsupported type: ${schema().type}")
    }
}

private fun AvroSchema.Field.toNamedValue(genericRecord: GenericRecord): Schema.Named {
    return when (schema().type) {
        AvroSchema.Type.STRING -> Schema.Named(name(), Schema.String(genericRecord[name()].toString()))
        AvroSchema.Type.INT -> Schema.Named(name(), Schema.Int(genericRecord[name()] as Int))
        AvroSchema.Type.LONG -> Schema.Named(name(), Schema.Long(genericRecord[name()] as Long))
        else -> throw IllegalArgumentException("Unsupported type: ${schema().type}")
    }
}

fun DataStream.toAvroGenericRecord(): GenericRecord {
    val genericRecord = GenericData.Record(toAvroSchema())

    //TODO we shouldn't assume top level value is a struct
    require(schema is Schema.Struct) { "Top level value must be a struct" }
    schema.value.forEach { namedValue ->
        genericRecord.put(namedValue.name, namedValue.value.value)
    }

    return genericRecord
}
