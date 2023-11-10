package io.typestream.kafka.protobuf

import com.squareup.wire.schema.internal.parser.MessageElement

data class ProtoSchema(val schemaString: String, val message: MessageElement) {
    override fun toString() = schemaString
}
