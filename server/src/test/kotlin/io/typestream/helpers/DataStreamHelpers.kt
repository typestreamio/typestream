package io.typestream.helpers

import io.typestream.compiler.types.DataStream
import io.typestream.compiler.types.schema.Schema
import java.util.UUID

fun author(
    id: Schema.UUID = Schema.UUID(UUID.randomUUID()),
    name: Schema.String = Schema.String.empty,
) = DataStream(
    "/dev/kafka/local/topics/authors",
    Schema.Struct(listOf(Schema.Named("id", id), Schema.Named("name", name)))
)

fun book(
    id: Schema.UUID = Schema.UUID(UUID.randomUUID()),
    title: String,
    wordCount: Int = 42,
) = DataStream(
    "/dev/kafka/local/topics/books",
    Schema.Struct(
        listOf(
            Schema.Named("id", id),
            Schema.Named("title", Schema.String(title)),
            Schema.Named("word_count", Schema.Int(wordCount))
        )
    )
)
