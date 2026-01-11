package io.typestream.geoip

import com.maxmind.geoip2.DatabaseReader
import io.github.oshai.kotlinlogging.KotlinLogging
import java.io.File
import java.net.InetAddress

class GeoIpService(private val databasePath: String) {
    private val logger = KotlinLogging.logger {}

    private val reader: DatabaseReader? by lazy {
        val file = File(databasePath)
        if (file.exists()) {
            logger.info { "Loading GeoIP database from $databasePath" }
            DatabaseReader.Builder(file).build()
        } else {
            logger.warn { "GeoIP database not found at $databasePath - lookups will return UNKNOWN" }
            null
        }
    }

    fun lookup(ipAddress: String): String {
        if (reader == null) {
            return "UNKNOWN"
        }

        return try {
            val inetAddress = InetAddress.getByName(ipAddress)
            reader?.country(inetAddress)?.country?.isoCode ?: "UNKNOWN"
        } catch (e: Exception) {
            logger.debug { "GeoIP lookup failed for $ipAddress: ${e.message}" }
            "UNKNOWN"
        }
    }
}
