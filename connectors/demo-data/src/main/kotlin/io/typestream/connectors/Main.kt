package io.typestream.connectors

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.main
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import io.github.oshai.kotlinlogging.KotlinLogging
import io.typestream.connectors.coinbase.CoinbaseConnector

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

fun main(args: Array<String>) = DemoData()
    .subcommands(CoinbaseCommand())
    .main(args)
