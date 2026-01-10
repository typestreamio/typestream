package io.typestream.connectors.coinbase

import io.typestream.connectors.avro.CryptoTicker
import io.typestream.connectors.kafka.MessageSender
import org.apache.avro.specific.SpecificRecord
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@Timeout(20, unit = TimeUnit.SECONDS)
internal class CoinbaseConnectorTest {

    @Test
    fun `receives ticker message from Coinbase WebSocket`() {
        val receivedMessages = CopyOnWriteArrayList<Pair<String, CryptoTicker>>()
        val messageLatch = CountDownLatch(1)

        val mockSender = object : MessageSender {
            override fun send(key: String, value: SpecificRecord) {
                receivedMessages.add(key to value as CryptoTicker)
                messageLatch.countDown()
            }

            override fun close() {}
        }

        val connector = CoinbaseConnector(listOf("BTC-USD"), mockSender)
        connector.start()

        val received = messageLatch.await(15, TimeUnit.SECONDS)
        connector.close()

        assertThat(received).withFailMessage("Did not receive ticker message within 15 seconds").isTrue()
        assertThat(receivedMessages).isNotEmpty()

        val (key, ticker) = receivedMessages.first()
        assertThat(key).isEqualTo("BTC-USD")
        assertThat(ticker.productId).isEqualTo("BTC-USD")
        assertThat(ticker.price).isNotBlank()
    }
}
