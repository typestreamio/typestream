package io.typestream.testing.model

import com.google.protobuf.Timestamp
import java.time.Instant
import java.util.UUID
import io.typestream.testing.proto.PageView as ProtoPageView

fun randomIpv4() = (0..3).map { (0..255).random() }.joinToString(".")

data class PageView(override val id: String, val bookId: String, val ipAddress: String = randomIpv4()) : TestRecord {
    override fun toAvro(): io.typestream.testing.avro.PageView = io.typestream.testing.avro.PageView.newBuilder()
        .setBookId(UUID.fromString(bookId))
        .setIpAddress(ipAddress)
        .setViewedAt(Instant.now())
        .build()

    override fun toProto(): ProtoPageView {
        val now = Instant.now()
        return ProtoPageView.newBuilder()
            .setBookId(bookId)
            .setIpAddress(ipAddress)
            .setViewedAt(Timestamp.newBuilder().setSeconds(now.epochSecond).setNanos(now.nano).build())
            .build()
    }
}
