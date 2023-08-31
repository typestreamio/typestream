package io.typestream.compiler.types

import kotlinx.serialization.Serializable

@Serializable
enum class Encoding {
    STRING, NUMBER, JSON, AVRO, PROTOBUF;

    operator fun plus(other: Encoding) = if (this == other) {
        this
    } else {
        JSON
    }
}
