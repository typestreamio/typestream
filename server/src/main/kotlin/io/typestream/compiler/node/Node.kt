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
    data class Each(override val id: String, val fn: (KeyValue) -> Unit) : Node

    @Serializable
    data class Sink(override val id: String, val output: DataStream, val encoding: Encoding) : Node

    @Serializable
    data class GeoIp(override val id: String, val ipField: String, val outputField: String) : Node

    @Serializable
    data class Inspector(override val id: String, val label: String = "") : Node

    @Serializable
    data class ReduceLatest(override val id: String) : Node

    @Serializable
    data class TextExtractor(override val id: String, val filePathField: String, val outputField: String) : Node

    @Serializable
    data class EmbeddingGenerator(
        override val id: String,
        val textField: String,
        val outputField: String,
        val model: String
    ) : Node

    @Serializable
    data class OpenAiTransformer(
        override val id: String,
        val prompt: String,
        val outputField: String,
        val model: String
    ) : Node
}
