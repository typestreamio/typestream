package io.typestream.testing.model

import java.util.UUID
import io.typestream.testing.avro.Author as AvroAuthor
import io.typestream.testing.proto.Author as ProtoAuthor

data class Author(override val id: String = UUID.randomUUID().toString(), val name: String) : TestRecord {
    override fun toAvro(): AvroAuthor = AvroAuthor.newBuilder()
        .setId(UUID.fromString(id))
        .setName(name)
        .build()

    override fun toProto(): ProtoAuthor = ProtoAuthor.newBuilder()
        .setId(id)
        .setName(name)
        .build()
}
