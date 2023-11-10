package io.typestream.testing.model

import java.util.UUID
import io.typestream.testing.avro.Book as AvroBook
import io.typestream.testing.proto.Book as ProtoBook

data class Book(
    override val id: String = UUID.randomUUID().toString(),
    val title: String,
    val authorId: String,
    val wordCount: Int,
) : TestRecord {
    override fun toAvro(): AvroBook = AvroBook.newBuilder()
        .setId(UUID.fromString(id))
        .setTitle(title)
        .setAuthorId(UUID.fromString(authorId))
        .setWordCount(wordCount)
        .build()

    override fun toProto(): ProtoBook = ProtoBook.newBuilder()
        .setId(id)
        .setTitle(title)
        .setAuthorId(authorId)
        .setWordCount(wordCount)
        .build()
}
