package io.typestream.kafka.protobuf

import com.github.os72.protobuf.dynamic.DynamicSchema
import com.github.os72.protobuf.dynamic.MessageDefinition
import com.google.protobuf.DynamicMessage
import com.google.protobuf.Message
import com.squareup.wire.schema.Location
import com.squareup.wire.schema.internal.parser.FieldElement
import com.squareup.wire.schema.internal.parser.MessageElement
import com.squareup.wire.schema.internal.parser.ProtoParser
import com.squareup.wire.schema.internal.parser.TypeElement

fun FieldElement.findDefaultValue(): Any {
    return when (type) {
        "string" -> ""
        "int32" -> 0
        "int64" -> 0L
        "float" -> 0.0f
        "double" -> 0.0
        "bool" -> false
        else -> error("unknown type $type")
    }
}

object ProtoParser {
    fun parse(schema: String): ProtoSchema {
        val file = ProtoParser.parse(Location.get(""), schema)

        //TODO find the right type
        val messageElement: TypeElement = file.types.first()
        require(messageElement is MessageElement)

        return ProtoSchema(schema, messageElement)
    }

    fun buildMessage(name: String, fields: List<ProtoField>): Message {
        val schemaBuilder: DynamicSchema.Builder = DynamicSchema.newBuilder()
        schemaBuilder.setName("$name.proto")

        val messageDefinition = MessageDefinition.newBuilder(name)

        fields.forEach { field ->
            messageDefinition.addField("optional", field.element.type, field.element.name, field.element.tag)
            //TODO handle dependencies
        }
        schemaBuilder.addMessageDefinition(messageDefinition.build())

        val schema: DynamicSchema = schemaBuilder.build()

        val messageBuilder: DynamicMessage.Builder = schema.newMessageBuilder(name)

        fields.forEach { field ->
            messageBuilder.setField(
                messageBuilder.descriptorForType.findFieldByName(field.element.name),
                field.value
            )
        }

        return messageBuilder.build()
    }
}
