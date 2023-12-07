package io.typestream.kafka.protobuf

import com.squareup.wire.schema.internal.parser.FieldElement

data class ProtoField(val element: FieldElement, val value: Any?)
