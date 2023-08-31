package io.typestream.testing.avro

import org.apache.avro.specific.SpecificRecordBase
import org.apache.kafka.clients.producer.ProducerRecord
import java.time.Instant
import java.util.UUID

fun buildAuthor(name: String): io.typestream.testing.avro.Author =
    io.typestream.testing.avro.Author.newBuilder().setId(UUID.randomUUID()).setName(name).build()

fun buildBook(title: String, wordCount: Int, authorId: UUID): io.typestream.testing.avro.Book =
    io.typestream.testing.avro.Book.newBuilder().setId(UUID.randomUUID()).setAuthorId(authorId).setTitle(title)
        .setWordCount(wordCount).build()

fun buildRating(userId: UUID, bookId: UUID, rating: Int): io.typestream.testing.avro.Rating =
    io.typestream.testing.avro.Rating.newBuilder().setBookId(bookId).setUserId(userId).setRatedAt(Instant.now())
        .setRating(rating).build()

fun buildUser(name: String): io.typestream.testing.avro.User =
    io.typestream.testing.avro.User.newBuilder().setId(UUID.randomUUID()).setName(name).build()

fun <K : SpecificRecordBase> toProducerRecords(
    topic: String,
    vararg records: K,
    id: (K) -> String = { r -> r.get("id").toString() },
) = records.map { record -> ProducerRecord(topic, id(record), record) }
