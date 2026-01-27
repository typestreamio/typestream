package io.typestream.connectors

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.main
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.double
import com.github.ajalt.clikt.parameters.types.long
import io.github.oshai.kotlinlogging.KotlinLogging
import io.typestream.connectors.coinbase.CoinbaseConnector
import io.typestream.connectors.fileuploads.FileUploadsConnector
import io.typestream.connectors.webvisits.WebVisitsConnector
import io.typestream.connectors.wikipedia.WikipediaConnector

private val logger = KotlinLogging.logger {}

class DemoData : CliktCommand(name = "demo-data") {
    override fun run() = Unit
}

class CoinbaseCommand : CliktCommand(name = "coinbase") {
    private val products by option("--products", "-p", help = "Comma-separated list of product IDs (e.g., BTC-USD,ETH-USD)")
        .default("BTC-USD,ETH-USD")

    override fun run() {
        val productList = products.split(",").map { it.trim() }
        logger.info { "Starting Coinbase connector with products: $productList" }

        val connector = CoinbaseConnector(productList)

        Runtime.getRuntime().addShutdownHook(Thread {
            logger.info { "Received shutdown signal" }
            connector.close()
        })

        connector.start()
        connector.awaitTermination()
    }
}

class WikipediaCommand : CliktCommand(name = "wikipedia") {
    private val wikis by option("--wikis", "-w", help = "Comma-separated wikis to filter (e.g., enwiki,dewiki). Empty = all wikis")
        .default("")

    override fun run() {
        val wikiList = wikis.split(",").map { it.trim() }.filter { it.isNotBlank() }
        logger.info { "Starting Wikipedia connector" + if (wikiList.isNotEmpty()) " for wikis: $wikiList" else " (all wikis)" }

        val connector = WikipediaConnector(wikiList)

        Runtime.getRuntime().addShutdownHook(Thread {
            logger.info { "Received shutdown signal" }
            connector.close()
        })

        connector.start()
        connector.awaitTermination()
    }
}

class WebVisitsCommand : CliktCommand(name = "webvisits") {
    private val rate by option("--rate", "-r", help = "Average events per second")
        .double()
        .default(10.0)

    private val countries by option("--countries", "-c", help = "Comma-separated country codes (default: weighted distribution)")
        .default("")

    private val topic by option("--topic", "-t", help = "Kafka topic name")
        .default("web_visits")

    override fun run() {
        val countryList = countries.takeIf { it.isNotBlank() }
            ?.split(",")
            ?.map { it.trim().uppercase() }

        logger.info { "Starting WebVisits connector" }
        logger.info { "  Rate: ~$rate events/sec" }
        logger.info { "  Countries: ${countryList?.joinToString(",") ?: "default weighted distribution"}" }
        logger.info { "  Topic: $topic" }

        val connector = WebVisitsConnector(
            ratePerSecond = rate,
            countries = countryList,
            topic = topic
        )

        Runtime.getRuntime().addShutdownHook(Thread {
            logger.info { "Received shutdown signal" }
            connector.close()
        })

        connector.start()
        connector.awaitTermination()
    }
}

class FileUploadsCommand : CliktCommand(name = "fileuploads") {
    private val outputDir by option("--output-dir", "-d", help = "Directory to create sample files")
        .default("/tmp/typestream-files")

    private val rate by option("--rate", "-r", help = "Records per second")
        .double()
        .default(1.0)

    private val maxMessages by option("--max", "-m", help = "Maximum number of records to insert (0 = unlimited)")
        .long()
        .default(50)

    private val jdbcUrl by option("--jdbc-url", help = "PostgreSQL JDBC URL")
        .default(System.getenv("POSTGRES_JDBC_URL") ?: "jdbc:postgresql://localhost:5432/demo")

    private val jdbcUser by option("--jdbc-user", help = "PostgreSQL username")
        .default(System.getenv("POSTGRES_USER") ?: "typestream")

    private val jdbcPassword by option("--jdbc-password", help = "PostgreSQL password")
        .default(System.getenv("POSTGRES_PASSWORD") ?: "typestream")

    override fun run() {
        logger.info { "Starting FileUploads connector (PostgreSQL CDC mode)" }
        logger.info { "  Output directory: $outputDir" }
        logger.info { "  Rate: ~$rate records/sec" }
        logger.info { "  Max records: $maxMessages" }
        logger.info { "  JDBC URL: $jdbcUrl" }
        logger.info { "  Records will be captured by Debezium and published to Kafka" }

        val connector = FileUploadsConnector(
            outputDir = outputDir,
            ratePerSecond = rate,
            maxMessages = maxMessages,
            jdbcUrl = jdbcUrl,
            jdbcUser = jdbcUser,
            jdbcPassword = jdbcPassword
        )

        Runtime.getRuntime().addShutdownHook(Thread {
            logger.info { "Received shutdown signal" }
            connector.close()
        })

        connector.start()
        connector.awaitTermination()
    }
}

fun main(args: Array<String>) = DemoData()
    .subcommands(CoinbaseCommand(), WikipediaCommand(), WebVisitsCommand(), FileUploadsCommand())
    .main(args)
