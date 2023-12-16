package io.typestream.compiler.types.datastream

import io.typestream.compiler.types.DataStream
import io.typestream.compiler.types.schema.Schema

fun DataStream.join(b: DataStream): DataStream {
    val path = if (path == b.path) path else "${path}_${b.path.substringAfterLast("/")}"

    val schema = Schema.Struct(listOf(Schema.Field(name, schema), Schema.Field(b.name, b.schema)))

    return DataStream(path, schema)
}
