package io.typestream.compiler.types.datastream

import io.typestream.compiler.types.DataStream
import io.typestream.compiler.types.schema.Schema
import org.apache.kafka.common.utils.Bytes

fun DataStream.Companion.fromBytes(path: String, value: Bytes) = DataStream(path, Schema.String(value.toString()))

fun DataStream.toBytes(): Bytes = Bytes.wrap(schema.value.toString().toByteArray())
