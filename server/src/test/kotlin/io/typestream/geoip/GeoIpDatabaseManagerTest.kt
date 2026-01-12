package io.typestream.geoip

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path

internal class GeoIpDatabaseManagerTest {

    @Nested
    inner class EnsureDatabase {

        @Test
        fun `returns true when database file already exists`(@TempDir tempDir: Path) {
            val dbDir = tempDir.toString()
            val manager = GeoIpDatabaseManager(databaseDir = dbDir, downloadEnabled = false)

            // Create the database file
            File(manager.databasePath).apply {
                parentFile.mkdirs()
                writeText("mock database content")
            }

            val result = manager.ensureDatabase()

            assertThat(result).isTrue
        }

        @Test
        fun `returns false when file missing and download disabled`(@TempDir tempDir: Path) {
            val dbDir = tempDir.resolve("nonexistent").toString()
            val manager = GeoIpDatabaseManager(databaseDir = dbDir, downloadEnabled = false)

            val result = manager.ensureDatabase()

            assertThat(result).isFalse
        }

        @Test
        fun `is idempotent - can be called multiple times`(@TempDir tempDir: Path) {
            val dbDir = tempDir.toString()
            val manager = GeoIpDatabaseManager(databaseDir = dbDir, downloadEnabled = false)

            // Create the database file
            File(manager.databasePath).apply {
                parentFile.mkdirs()
                writeText("mock database content")
            }

            val result1 = manager.ensureDatabase()
            val result2 = manager.ensureDatabase()

            assertThat(result1).isTrue
            assertThat(result2).isTrue
        }
    }

    @Nested
    inner class DatabasePath {

        @Test
        fun `returns correct path based on database directory`(@TempDir tempDir: Path) {
            val dbDir = tempDir.toString()
            val manager = GeoIpDatabaseManager(databaseDir = dbDir)

            assertThat(manager.databasePath).isEqualTo("$dbDir/dbip-country-lite.mmdb")
        }
    }

    @Nested
    inner class DefaultDatabaseDir {

        @Test
        fun `returns path under user home directory`() {
            val defaultDir = GeoIpDatabaseManager.defaultDatabaseDir()
            val userHome = System.getProperty("user.home")

            assertThat(defaultDir).startsWith(userHome)
            assertThat(defaultDir).contains(".typestream")
            assertThat(defaultDir).contains("geoip")
        }
    }
}
