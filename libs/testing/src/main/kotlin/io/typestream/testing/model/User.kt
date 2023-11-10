package io.typestream.testing.model

import java.util.UUID
import io.typestream.testing.avro.User as AvroUser
import io.typestream.testing.proto.User as ProtoUser

data class User(override val id: String = UUID.randomUUID().toString(), val name: String) : TestRecord {
    override fun toAvro(): AvroUser = AvroUser.newBuilder()
        .setId(UUID.fromString(id))
        .setName(name)
        .build()

    override fun toProto(): ProtoUser = ProtoUser.newBuilder()
        .setId(id)
        .setName(name)
        .build()
}
