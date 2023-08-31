package io.typestream.compiler.node

import kotlinx.serialization.Serializable

@Serializable
data class JoinType(val byKey: Boolean = true, val isLookup: Boolean = false)
