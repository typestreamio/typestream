package io.typestream.compiler.node

import io.typestream.compiler.ast.Predicate
import io.typestream.compiler.types.DataStream
import io.typestream.compiler.types.Encoding
import kotlinx.serialization.Serializable

@Serializable
sealed interface Node {
    val id: String

    @Serializable
    data class Count(override val id: String) : Node

    @Serializable
    data class Filter(override val id: String, val byKey: Boolean, val predicate: Predicate) : Node

    @Serializable
    data class Group(override val id: String, val keyMapper: (KeyValue) -> DataStream) : Node

    @Serializable
    data class Join(override val id: String, val with: DataStream, val joinType: JoinType) : Node

    @Serializable
    data class Map(override val id: String, val mapper: (KeyValue) -> KeyValue) : Node

    @Serializable
    data class NoOp(override val id: String) : Node

    @Serializable
    data class ShellSource(override val id: String, val data: List<DataStream>) : Node

    @Serializable
    data class StreamSource(override val id: String, val dataStream: DataStream, val encoding: Encoding) : Node

    @Serializable
    data class Sink(override val id: String, val output: DataStream, val encoding: Encoding) : Node
}
