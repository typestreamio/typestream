package io.typestream.geoip

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

internal class GeoIpServiceTest {

    @Nested
    inner class LookupWithMissingDatabase {
        @Test
        fun `lookup returns null when database file does not exist`() {
            val service = GeoIpService("/nonexistent/path/GeoLite2-Country.mmdb")

            val result = service.lookup("8.8.8.8")

            assertThat(result).isNull()
        }

        @Test
        fun `isAvailable returns false when database file does not exist`() {
            val service = GeoIpService("/nonexistent/path/GeoLite2-Country.mmdb")

            assertThat(service.isAvailable()).isFalse
        }
    }

    @Nested
    inner class LookupWithInvalidInput {
        @Test
        fun `lookup returns null for invalid IP address`(@TempDir tempDir: Path) {
            val service = GeoIpService(tempDir.resolve("nonexistent.mmdb").toString())

            val result = service.lookup("not-an-ip-address")

            assertThat(result).isNull()
        }

        @Test
        fun `lookup returns null for empty IP address`(@TempDir tempDir: Path) {
            val service = GeoIpService(tempDir.resolve("nonexistent.mmdb").toString())

            val result = service.lookup("")

            assertThat(result).isNull()
        }
    }

    @Nested
    inner class ResourceManagement {
        @Test
        fun `close can be called without error on service with missing database`() {
            val service = GeoIpService("/nonexistent/path/GeoLite2-Country.mmdb")

            // Should not throw
            service.close()
        }

        @Test
        fun `service implements Closeable`() {
            val service = GeoIpService("/nonexistent/path/GeoLite2-Country.mmdb")

            assertThat(service).isInstanceOf(java.io.Closeable::class.java)
        }
    }
}
