package io.typestream.kafka.schemaregistry

import kotlinx.serialization.Serializable

@Serializable
data class RegisterSchemaResponse(val id: Int)
