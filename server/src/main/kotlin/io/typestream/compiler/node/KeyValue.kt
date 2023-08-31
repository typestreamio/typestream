package io.typestream.compiler.node

import io.typestream.compiler.types.DataStream
import kotlinx.serialization.Serializable

@Serializable
data class KeyValue(val key: DataStream, val value: DataStream)
