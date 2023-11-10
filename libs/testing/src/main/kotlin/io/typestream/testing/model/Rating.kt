package io.typestream.testing.model

import com.google.protobuf.Timestamp
import java.time.Instant
import java.util.UUID
import io.typestream.testing.avro.Rating as AvroRating
import io.typestream.testing.proto.Rating as ProtoRating

data class Rating(override val id: String, val userId: String, val bookId: String, val rating: Int) : TestRecord {
    override fun toAvro(): AvroRating = AvroRating.newBuilder()
        .setUserId(UUID.fromString(userId))
        .setBookId(UUID.fromString(bookId))
        .setRating(rating)
        .setRatedAt(Instant.now())
        .build()

    override fun toProto(): ProtoRating {
        val now = Instant.now()
        return ProtoRating.newBuilder()
            .setUserId(userId)
            .setBookId(bookId)
            .setRating(rating)
            .setRatedAt(Timestamp.newBuilder().setSeconds(now.epochSecond).setNanos(now.nano).build())
            .build()
    }
}
