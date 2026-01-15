package io.typestream.connectors.fileuploads

import io.github.oshai.kotlinlogging.KotlinLogging
import io.typestream.connectors.Config
import io.typestream.connectors.avro.FileUpload
import io.typestream.connectors.kafka.MessageSender
import io.typestream.connectors.kafka.Producer
import java.io.File
import java.time.Instant
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlin.random.Random

private const val DEFAULT_TOPIC = "file_uploads"
private const val RETENTION_MS = 60 * 60 * 1000L // 1 hour retention

/**
 * Represents a sample file to be created and referenced in file upload messages.
 */
data class SampleFile(
    val fileName: String,
    val contentType: String,
    val content: String
)

/**
 * A demo-data connector that generates synthetic file upload messages.
 *
 * On startup, creates sample text files in the output directory.
 * During runtime, generates messages (at configurable rate) that randomly
 * reference one of these file paths.
 *
 * This is useful for testing the TextExtractor node, which extracts
 * text content from files using Apache Tika.
 */
class FileUploadsConnector(
    private val outputDir: String = "/tmp/typestream-files",
    private val ratePerSecond: Double = 1.0,
    topic: String = DEFAULT_TOPIC,
    private val sender: MessageSender = Producer(Config.fromEnv(topic).copy(retentionMs = RETENTION_MS))
) : AutoCloseable {
    private val logger = KotlinLogging.logger {}
    private val running = AtomicBoolean(true)
    private val closeLatch = CountDownLatch(1)
    private val messageCount = AtomicLong(0)

    private val sampleFiles = listOf(
        SampleFile(
            "invoice_001.txt",
            "text/plain",
            """
            INVOICE #INV-2024-001
            Date: January 15, 2024
            Customer: Acme Corporation

            ITEMS:
            Widget A (x3) .......... $45.00
            Widget B (x2) .......... $30.00
            Service Fee ............ $25.00
            ----------------------------
            Subtotal: $100.00
            Tax (10%): $10.00
            ----------------------------
            Total: $110.00

            Payment Terms: Net 30
            Thank you for your business!
            """.trimIndent()
        ),
        SampleFile(
            "article_tech.txt",
            "text/plain",
            """
            Streaming Data Processing: The Future of Real-Time Analytics

            By Jane Smith | Tech Today | January 2024

            In the rapidly evolving world of data engineering, streaming data
            processing has emerged as a critical capability for businesses
            seeking real-time insights. Unlike traditional batch processing,
            stream processing enables organizations to analyze data as it
            arrives, enabling faster decision-making.

            Key benefits include:
            - Reduced latency from hours to milliseconds
            - Real-time dashboards and alerting
            - Immediate fraud detection and prevention
            - Dynamic pricing and inventory management

            Technologies like Apache Kafka, Apache Flink, and TypeStream
            are leading the charge in making stream processing accessible
            to development teams of all sizes.
            """.trimIndent()
        ),
        SampleFile(
            "email_support.txt",
            "text/plain",
            """
            From: customer.support@example.com
            To: valued.customer@company.com
            Subject: Re: Order #12345 - Shipping Update

            Dear Valued Customer,

            Thank you for reaching out regarding your recent order.

            We're pleased to inform you that your package has been shipped
            and is currently in transit. Here are the details:

            Order Number: #12345
            Tracking Number: 1Z999AA10123456784
            Carrier: UPS Ground
            Estimated Delivery: January 20, 2024

            You can track your package using the link below:
            https://www.ups.com/track?tracknum=1Z999AA10123456784

            If you have any questions, please don't hesitate to contact us.

            Best regards,
            Customer Support Team
            """.trimIndent()
        ),
        SampleFile(
            "report_quarterly.txt",
            "text/plain",
            """
            Q4 2023 QUARTERLY BUSINESS REPORT
            Prepared by: Finance Department
            Date: January 5, 2024

            EXECUTIVE SUMMARY
            ------------------
            Q4 2023 showed strong growth across all business segments,
            exceeding revenue targets by 15% and maintaining healthy
            profit margins despite market challenges.

            KEY METRICS
            -----------
            Revenue: $12.5M (+18% YoY)
            Gross Margin: 42%
            Net Profit: $2.1M
            Customer Acquisition: 1,234 new accounts
            Customer Retention: 94%

            HIGHLIGHTS
            ----------
            - Launched new product line in October
            - Expanded to 3 new regional markets
            - Reduced operational costs by 8%
            - Hired 45 new team members

            OUTLOOK
            -------
            Q1 2024 projects continued growth with focus on
            international expansion and product innovation.
            """.trimIndent()
        ),
        SampleFile(
            "notes_meeting.txt",
            "text/plain",
            """
            MEETING NOTES
            Project: Data Platform Modernization
            Date: January 10, 2024
            Attendees: Alice, Bob, Charlie, Diana

            AGENDA
            ------
            1. Sprint review
            2. Technical decisions
            3. Q1 planning

            DISCUSSION
            ----------
            Alice presented the completed work from Sprint 23:
            - Kafka cluster upgrade completed successfully
            - Schema registry migration finished
            - Performance improved by 40%

            Bob raised concerns about scaling:
            - Current architecture handles 10K events/sec
            - Need to reach 100K events/sec by Q2
            - Proposed solution: horizontal scaling with partitioning

            ACTION ITEMS
            ------------
            [ ] Alice: Document scaling architecture (Due: Jan 15)
            [ ] Bob: POC for horizontal scaling (Due: Jan 20)
            [ ] Charlie: Update monitoring dashboards (Due: Jan 12)
            [ ] Diana: Schedule follow-up meeting (Due: Jan 11)

            NEXT MEETING: January 17, 2024 at 2:00 PM
            """.trimIndent()
        )
    )

    private val uploaders = listOf(
        "alice@example.com",
        "bob@example.com",
        "charlie@example.com",
        "diana@example.com",
        "system@automated.io"
    )

    fun start() {
        logger.info { "Starting FileUploads connector" }
        logger.info { "  Output directory: $outputDir" }
        logger.info { "  Rate: ~$ratePerSecond events/sec" }

        createSampleFiles()

        Thread {
            try {
                val delayMs = (1000.0 / ratePerSecond).toLong().coerceAtLeast(1)

                while (running.get()) {
                    val upload = generateUpload()
                    sender.send(upload.id, upload)

                    val count = messageCount.incrementAndGet()
                    if (count % 10 == 0L) {
                        logger.info { "Produced $count file uploads (latest: ${upload.fileName})" }
                    } else {
                        logger.debug { "Produced upload: ${upload.id} -> ${upload.filePath}" }
                    }

                    Thread.sleep(delayMs)
                }
            } catch (e: InterruptedException) {
                logger.info { "Generator thread interrupted" }
            } finally {
                closeLatch.countDown()
            }
        }.apply {
            name = "fileuploads-generator"
            isDaemon = true
            start()
        }
    }

    private fun createSampleFiles() {
        val dir = File(outputDir)
        if (!dir.exists()) {
            logger.info { "Creating output directory: $outputDir" }
            dir.mkdirs()
        }

        sampleFiles.forEach { sample ->
            val file = File(dir, sample.fileName)
            if (!file.exists()) {
                logger.info { "Creating sample file: ${file.absolutePath}" }
                file.writeText(sample.content)
            } else {
                logger.debug { "Sample file already exists: ${file.absolutePath}" }
            }
        }

        logger.info { "Created ${sampleFiles.size} sample files in $outputDir" }
    }

    private fun generateUpload(): FileUpload {
        val sample = sampleFiles[Random.nextInt(sampleFiles.size)]
        val uploader = uploaders[Random.nextInt(uploaders.size)]
        val filePath = "$outputDir/${sample.fileName}"

        return FileUpload.newBuilder()
            .setId(UUID.randomUUID().toString())
            .setFilePath(filePath)
            .setFileName(sample.fileName)
            .setContentType(sample.contentType)
            .setUploadedBy(uploader)
            .setTimestamp(Instant.now())
            .build()
    }

    fun awaitTermination() {
        closeLatch.await()
    }

    override fun close() {
        logger.info { "Shutting down FileUploads connector (produced ${messageCount.get()} messages)..." }
        running.set(false)
        sender.close()
    }
}
