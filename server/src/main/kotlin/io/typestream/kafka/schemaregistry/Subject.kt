package io.typestream.kafka.schemaregistry

import kotlinx.serialization.Serializable

@Serializable
data class Subject(
    val subject: String,
    val id: Int,
    val version: Int,
    val schemaType: SchemaType = SchemaType.AVRO,
    val schema: String,
)
