package io.typestream.kafka.protobuf

import com.squareup.wire.schema.internal.parser.MessageElement

fun MessageElement.toMessage() =
    ProtoParser.buildMessage(name, fields.map { field -> ProtoField(field, field.findDefaultValue()) })
