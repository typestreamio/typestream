package io.typestream.config

import kotlinx.serialization.Serializable

@Serializable
data class GeoIpConfig(
    val databasePath: String = "/etc/typestream/GeoLite2-Country.mmdb"
)
