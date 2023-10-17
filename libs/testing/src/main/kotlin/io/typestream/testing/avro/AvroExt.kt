package io.typestream.testing.avro

import org.apache.avro.specific.SpecificRecordBase
import org.apache.kafka.clients.producer.ProducerRecord
import java.time.Instant
import java.util.UUID

fun buildAuthor(name: String): Author =
    Author.newBuilder().setId(UUID.randomUUID()).setName(name).build()

fun buildBook(title: String, wordCount: Int, authorId: UUID): Book =
    Book.newBuilder().setId(UUID.randomUUID()).setAuthorId(authorId).setTitle(title)
        .setWordCount(wordCount).build()

fun buildRating(userId: UUID, bookId: UUID, rating: Int): Rating =
    Rating.newBuilder().setBookId(bookId).setUserId(userId).setRatedAt(Instant.now())
        .setRating(rating).build()

fun buildUser(name: String): User =
    User.newBuilder().setId(UUID.randomUUID()).setName(name).build()

fun <K : SpecificRecordBase> toProducerRecords(
    topic: String,
    vararg records: K,
    id: (K) -> String = { r -> r.get("id").toString() },
) = records.map { record -> ProducerRecord(topic, id(record), record) }
