package io.typestream.geoip

import io.github.oshai.kotlinlogging.KotlinLogging
import java.io.File
import java.io.FileOutputStream
import java.net.URI
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.zip.GZIPInputStream

/**
 * Manages the GeoIP database file, including auto-downloading if missing.
 * Uses DB-IP's free country database (CC-BY licensed).
 */
class GeoIpDatabaseManager(
    private val databaseDir: String = defaultDatabaseDir(),
    private val downloadEnabled: Boolean = true
) {
    private val logger = KotlinLogging.logger {}

    val databasePath: String
        get() = "$databaseDir/$DATABASE_FILENAME"

    /**
     * Ensures the database is available, downloading if necessary.
     * This method is safe to call multiple times.
     *
     * @return true if database is available, false otherwise
     */
    fun ensureDatabase(): Boolean {
        val dbFile = File(databasePath)

        if (dbFile.exists()) {
            logger.info { "GeoIP database found at $databasePath" }
            return true
        }

        if (!downloadEnabled) {
            logger.warn { "GeoIP database not found at $databasePath and auto-download is disabled" }
            return false
        }

        return downloadDatabase()
    }

    /**
     * Downloads the latest DB-IP country database.
     */
    private fun downloadDatabase(): Boolean {
        val url = buildDownloadUrl()
        logger.info { "Downloading GeoIP database from $url" }

        return try {
            val dir = File(databaseDir)
            if (!dir.exists()) {
                dir.mkdirs()
                logger.info { "Created GeoIP database directory: $databaseDir" }
            }

            val tempFile = File("$databasePath.tmp")
            val targetFile = File(databasePath)

            URI(url).toURL().openStream().use { input ->
                GZIPInputStream(input).use { gzipInput ->
                    FileOutputStream(tempFile).use { output ->
                        gzipInput.copyTo(output)
                    }
                }
            }

            // Atomic move to final location
            tempFile.renameTo(targetFile)
            logger.info { "GeoIP database downloaded successfully to $databasePath" }
            true
        } catch (e: Exception) {
            logger.error(e) { "Failed to download GeoIP database: ${e.message}" }
            false
        }
    }

    private fun buildDownloadUrl(): String {
        val yearMonth = YearMonth.now()
        val formatted = yearMonth.format(DateTimeFormatter.ofPattern("yyyy-MM"))
        return "$DBIP_BASE_URL/dbip-country-lite-$formatted.mmdb.gz"
    }

    companion object {
        private const val DATABASE_FILENAME = "dbip-country-lite.mmdb"
        private const val DBIP_BASE_URL = "https://download.db-ip.com/free"

        fun defaultDatabaseDir(): String {
            val home = System.getProperty("user.home")
            return "$home/.typestream/geoip"
        }
    }
}
