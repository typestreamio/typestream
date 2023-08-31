package io.typestream.filesystem.catalog

import io.typestream.compiler.types.DataStream
import io.typestream.compiler.types.Encoding

data class Metadata(val dataStream: DataStream, val encoding: Encoding)
