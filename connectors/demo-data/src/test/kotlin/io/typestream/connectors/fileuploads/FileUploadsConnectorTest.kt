package io.typestream.connectors.fileuploads

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.io.TempDir
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.io.File
import java.nio.file.Path
import java.sql.DriverManager
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@Testcontainers
@Timeout(60, unit = TimeUnit.SECONDS)
@Disabled("Requires Docker environment. Re-enable when running locally with Docker available.")
internal class FileUploadsConnectorTest {

    companion object {
        @Container
        @JvmStatic
        val postgres: PostgreSQLContainer<*> = PostgreSQLContainer("postgres:16")
            .withDatabaseName("demo")
            .withUsername("typestream")
            .withPassword("typestream")

        @JvmStatic
        @BeforeAll
        fun setupSchema() {
            DriverManager.getConnection(
                postgres.jdbcUrl,
                postgres.username,
                postgres.password
            ).use { conn ->
                conn.createStatement().use { stmt ->
                    stmt.execute("""
                        CREATE TABLE file_uploads (
                            id VARCHAR(36) PRIMARY KEY,
                            file_path VARCHAR(512) NOT NULL,
                            file_name VARCHAR(255) NOT NULL,
                            content_type VARCHAR(100) NOT NULL,
                            uploaded_by VARCHAR(255) NOT NULL,
                            created_at TIMESTAMP DEFAULT NOW()
                        )
                    """.trimIndent())
                }
            }
        }

        @JvmStatic
        @AfterAll
        fun cleanup() {
            // Container cleanup handled by Testcontainers
        }
    }

    private fun createConnectionProvider(): ConnectionProvider {
        return ConnectionProvider {
            DriverManager.getConnection(
                postgres.jdbcUrl,
                postgres.username,
                postgres.password
            )
        }
    }

    private fun countRecords(): Int {
        return DriverManager.getConnection(
            postgres.jdbcUrl,
            postgres.username,
            postgres.password
        ).use { conn ->
            conn.createStatement().use { stmt ->
                stmt.executeQuery("SELECT COUNT(*) FROM file_uploads").use { rs ->
                    rs.next()
                    rs.getInt(1)
                }
            }
        }
    }

    private fun clearTable() {
        DriverManager.getConnection(
            postgres.jdbcUrl,
            postgres.username,
            postgres.password
        ).use { conn ->
            conn.createStatement().use { stmt ->
                stmt.execute("DELETE FROM file_uploads")
            }
        }
    }

    @Test
    fun `inserts file uploads into PostgreSQL with valid fields`(@TempDir tempDir: Path) {
        clearTable()

        val connector = FileUploadsConnector(
            outputDir = tempDir.toString(),
            ratePerSecond = 100.0,  // Fast for testing
            maxMessages = 5,
            connectionProvider = createConnectionProvider()
        )
        connector.start()
        connector.awaitTermination()
        connector.close()

        val count = countRecords()
        assertThat(count).isEqualTo(5)

        // Verify records have valid data
        DriverManager.getConnection(
            postgres.jdbcUrl,
            postgres.username,
            postgres.password
        ).use { conn ->
            conn.createStatement().use { stmt ->
                stmt.executeQuery("SELECT * FROM file_uploads").use { rs ->
                    while (rs.next()) {
                        assertThat(rs.getString("id")).isNotBlank()
                        assertThat(rs.getString("file_path")).startsWith(tempDir.toString())
                        assertThat(rs.getString("file_name")).isNotBlank()
                        assertThat(rs.getString("content_type")).isEqualTo("text/plain")
                        assertThat(rs.getString("uploaded_by")).contains("@")
                        assertThat(rs.getTimestamp("created_at")).isNotNull()
                    }
                }
            }
        }
    }

    @Test
    fun `creates sample files in output directory`(@TempDir tempDir: Path) {
        clearTable()

        val connector = FileUploadsConnector(
            outputDir = tempDir.toString(),
            ratePerSecond = 100.0,
            maxMessages = 1,
            connectionProvider = createConnectionProvider()
        )
        connector.start()
        connector.awaitTermination()
        connector.close()

        // Verify sample files were created
        val createdFiles = tempDir.toFile().listFiles()?.map { it.name } ?: emptyList()
        assertThat(createdFiles).contains(
            "invoice_001.txt",
            "article_tech.txt",
            "email_support.txt",
            "report_quarterly.txt",
            "notes_meeting.txt"
        )

        // Verify files have content
        val invoiceFile = File(tempDir.toFile(), "invoice_001.txt")
        assertThat(invoiceFile.readText()).contains("INVOICE")
    }

    @Test
    fun `references existing files in database records`(@TempDir tempDir: Path) {
        clearTable()

        val connector = FileUploadsConnector(
            outputDir = tempDir.toString(),
            ratePerSecond = 100.0,
            maxMessages = 10,
            connectionProvider = createConnectionProvider()
        )
        connector.start()
        connector.awaitTermination()
        connector.close()

        // All file paths should reference actual files
        DriverManager.getConnection(
            postgres.jdbcUrl,
            postgres.username,
            postgres.password
        ).use { conn ->
            conn.createStatement().use { stmt ->
                stmt.executeQuery("SELECT file_path FROM file_uploads").use { rs ->
                    while (rs.next()) {
                        val filePath = rs.getString("file_path")
                        val file = File(filePath)
                        assertThat(file.exists())
                            .withFailMessage("File should exist: $filePath")
                            .isTrue()
                    }
                }
            }
        }
    }

    @Test
    fun `stops after max messages reached`(@TempDir tempDir: Path) {
        clearTable()

        val connector = FileUploadsConnector(
            outputDir = tempDir.toString(),
            ratePerSecond = 100.0,
            maxMessages = 3,
            connectionProvider = createConnectionProvider()
        )
        connector.start()
        connector.awaitTermination()
        connector.close()

        val count = countRecords()
        assertThat(count).isEqualTo(3)
    }
}
