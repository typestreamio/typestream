package io.typestream.connectors.fileuploads

import io.typestream.connectors.avro.FileUpload
import io.typestream.connectors.kafka.MessageSender
import org.apache.avro.specific.SpecificRecord
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@Timeout(10, unit = TimeUnit.SECONDS)
internal class FileUploadsConnectorTest {

    @Test
    fun `generates file uploads with valid fields`(@TempDir tempDir: Path) {
        val receivedMessages = CopyOnWriteArrayList<Pair<String, FileUpload>>()
        val messageLatch = CountDownLatch(5)

        val mockSender = object : MessageSender {
            override fun send(key: String, value: SpecificRecord) {
                if (messageLatch.count > 0) {
                    receivedMessages.add(key to value as FileUpload)
                    messageLatch.countDown()
                }
            }
            override fun close() {}
        }

        val connector = FileUploadsConnector(
            outputDir = tempDir.toString(),
            ratePerSecond = 100.0,  // Fast for testing
            sender = mockSender
        )
        connector.start()

        val received = messageLatch.await(5, TimeUnit.SECONDS)
        connector.close()

        assertThat(received).withFailMessage("Did not receive 5 messages within timeout").isTrue()
        assertThat(receivedMessages).hasSize(5)

        receivedMessages.forEach { (key, upload) ->
            // Key should be upload ID
            assertThat(key).isEqualTo(upload.id)

            // Validate all fields
            assertThat(upload.id).isNotBlank()
            assertThat(upload.filePath).startsWith(tempDir.toString())
            assertThat(upload.fileName).isNotBlank()
            assertThat(upload.contentType).isEqualTo("text/plain")
            assertThat(upload.uploadedBy).contains("@")
            assertThat(upload.timestamp).isNotNull()
        }
    }

    @Test
    fun `creates sample files in output directory`(@TempDir tempDir: Path) {
        val messageLatch = CountDownLatch(1)

        val mockSender = object : MessageSender {
            override fun send(key: String, value: SpecificRecord) {
                messageLatch.countDown()
            }
            override fun close() {}
        }

        val connector = FileUploadsConnector(
            outputDir = tempDir.toString(),
            ratePerSecond = 100.0,
            sender = mockSender
        )
        connector.start()

        // Wait for at least one message to ensure files are created
        messageLatch.await(5, TimeUnit.SECONDS)
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
    fun `references existing files in messages`(@TempDir tempDir: Path) {
        val receivedMessages = CopyOnWriteArrayList<FileUpload>()
        val messageLatch = CountDownLatch(10)

        val mockSender = object : MessageSender {
            override fun send(key: String, value: SpecificRecord) {
                receivedMessages.add(value as FileUpload)
                messageLatch.countDown()
            }
            override fun close() {}
        }

        val connector = FileUploadsConnector(
            outputDir = tempDir.toString(),
            ratePerSecond = 100.0,
            sender = mockSender
        )
        connector.start()

        val received = messageLatch.await(5, TimeUnit.SECONDS)
        connector.close()

        assertThat(received).isTrue()

        // All file paths should reference actual files
        receivedMessages.forEach { upload ->
            val file = File(upload.filePath)
            assertThat(file.exists()).withFailMessage("File should exist: ${upload.filePath}").isTrue()
        }
    }
}
