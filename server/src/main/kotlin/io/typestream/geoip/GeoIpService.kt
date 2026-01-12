package io.typestream.geoip

import com.maxmind.geoip2.DatabaseReader
import io.github.oshai.kotlinlogging.KotlinLogging
import java.io.File
import java.net.InetAddress

/**
 * Service for performing GeoIP lookups using MaxMind GeoLite2 database.
 * This is a local database file (.mmdb) - no API calls, purely local lookups.
 */
class GeoIpService(private val databasePath: String = DEFAULT_DATABASE_PATH) {
    private val logger = KotlinLogging.logger {}

    private val reader: DatabaseReader? by lazy {
        val file = File(databasePath)
        if (file.exists()) {
            logger.info { "Loading GeoIP database from $databasePath" }
            DatabaseReader.Builder(file).build()
        } else {
            logger.warn { "GeoIP database not found at $databasePath. GeoIP lookups will return UNKNOWN." }
            null
        }
    }

    /**
     * Lookup the country code for an IP address.
     * @param ipAddress The IP address to lookup (IPv4 or IPv6)
     * @return ISO country code (e.g., "US", "GB") or null if not found
     */
    fun lookup(ipAddress: String): String? {
        return try {
            reader?.country(InetAddress.getByName(ipAddress))?.country?.isoCode
        } catch (e: Exception) {
            logger.debug { "GeoIP lookup failed for $ipAddress: ${e.message}" }
            null
        }
    }

    /**
     * Check if the GeoIP database is available.
     */
    fun isAvailable(): Boolean = reader != null

    companion object {
        const val DEFAULT_DATABASE_PATH = "/etc/typestream/GeoLite2-Country.mmdb"
    }
}
