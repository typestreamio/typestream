package io.typestream.compiler.types.datastream

import com.google.protobuf.Descriptors.FieldDescriptor
import com.google.protobuf.Message
import io.typestream.compiler.types.DataStream
import io.typestream.compiler.types.schema.Schema
import io.typestream.kafka.protobuf.ProtoField
import io.typestream.kafka.protobuf.ProtoParser
import io.typestream.kafka.protobuf.ProtoSchema
import io.typestream.kafka.protobuf.toMessage

fun DataStream.Companion.fromProtoMessage(path: String, message: Message): DataStream {
    val values = message.descriptorForType.fields.map { fieldDescriptor ->
        fieldDescriptor.toNamedValue(message)
    }

    return DataStream(path, Schema.Struct(values))
}

fun DataStream.Companion.fromProtoSchema(path: String, protoSchema: ProtoSchema): DataStream {
    val message = protoSchema.message.toMessage()
    val values = message.allFields.map { (fieldDescriptor, _) ->
        fieldDescriptor.toNamedValue(message)
    }

    return DataStream(path, Schema.Struct(values))
}

fun DataStream.toProtoSchema(): ProtoSchema {
    require(schema is Schema.Struct) { "top level value must be a struct" }

    val fields = schema.value.mapIndexed { index, namedValue ->
        "${toProtoType(namedValue.value)} ${namedValue.name} = ${index + 1};"
    }.joinToString("\n")

    val schemaDefinition = """
        syntax = "proto3";
        
        package io.typestream.proto;
        
        option java_package = "io.typestream.proto";
        option java_multiple_files = true;
        
        message ${path.replace("/", "_")} {
            $fields
        }
        """.trimIndent()

    return ProtoParser.parse(schemaDefinition)
}

private fun toProtoType(schema: Schema): String {
    return when (schema) {
        is Schema.UUID -> "string"
        is Schema.String -> "string"
        is Schema.Long -> "int64"
        is Schema.Int -> "int32"
        else -> error("Unsupported type: ${schema::class.simpleName}")
    }
}

private fun FieldDescriptor.toNamedValue(message: Message): Schema.Named {
    return when (type) {
        FieldDescriptor.Type.STRING -> Schema.Named(name, Schema.String(message.getField(this).toString()))
        FieldDescriptor.Type.INT32 -> Schema.Named(name, Schema.Int(message.getField(this) as Int))
        FieldDescriptor.Type.INT64 -> Schema.Named(name, Schema.Long(message.getField(this) as Long))

        else -> error("Unsupported type: $type")
    }
}

fun DataStream.toProtoMessage(): Message {
    require(schema is Schema.Struct) { "top level value must be a struct" }

    val message = ProtoParser.buildMessage(name, toProtoSchema().message.fields.map { field ->
        ProtoField(field, schema[field.name].value)
    })

    return message
}
