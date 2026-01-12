package io.typestream.geoip

import com.maxmind.geoip2.DatabaseReader
import io.github.oshai.kotlinlogging.KotlinLogging
import java.io.Closeable
import java.io.File
import java.net.InetAddress

/**
 * Service for performing GeoIP lookups using a local MMDB database.
 * Supports both MaxMind GeoLite2 and DB-IP formats.
 * This is a local database file (.mmdb) - no API calls, purely local lookups.
 */
open class GeoIpService(private val databasePath: String = defaultDatabasePath()) : Closeable {
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
    open fun lookup(ipAddress: String): String? {
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

    /**
     * Close the database reader to release resources.
     */
    override fun close() {
        reader?.close()
    }

    companion object {
        fun defaultDatabasePath(): String {
            val home = System.getProperty("user.home")
            return "$home/.typestream/geoip/dbip-country-lite.mmdb"
        }
    }
}
