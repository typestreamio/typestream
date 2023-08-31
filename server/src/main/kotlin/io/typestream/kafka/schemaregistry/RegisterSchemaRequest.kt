package io.typestream.kafka.schemaregistry

import kotlinx.serialization.Serializable

@Serializable
data class RegisterSchemaRequest(val schema: String)
