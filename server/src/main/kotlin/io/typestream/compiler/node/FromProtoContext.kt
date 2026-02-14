package io.typestream.compiler.node

import io.typestream.compiler.types.DataStream
import io.typestream.compiler.types.Encoding

data class FromProtoContext(
    val inferredSchemas: Map<String, DataStream>,
    val inferredEncodings: Map<String, Encoding>,
    val findDataStream: (String) -> DataStream
)
