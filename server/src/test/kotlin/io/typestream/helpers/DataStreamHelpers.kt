package io.typestream.helpers

import io.typestream.compiler.types.DataStream
import io.typestream.compiler.types.schema.Schema
import java.util.UUID

fun author(
    id: Schema.UUID = Schema.UUID(UUID.randomUUID()),
    name: Schema.String = Schema.String.zeroValue,
    topic: String = "authors",
) = DataStream(
    "/dev/kafka/local/topics/$topic",
    Schema.Struct(listOf(Schema.Field("id", id), Schema.Field("name", name)))
)

fun book(
    id: Schema.UUID = Schema.UUID(UUID.randomUUID()),
    title: String,
    wordCount: Int = 42,
    topic: String = "books",
) = DataStream(
    "/dev/kafka/local/topics/$topic",
    Schema.Struct(
        listOf(
            Schema.Field("id", id),
            Schema.Field("title", Schema.String(title)),
            Schema.Field("word_count", Schema.Int(wordCount))
        )
    )
)
