package io.typestream.connectors.wikipedia

import io.typestream.connectors.avro.WikipediaChange
import io.typestream.connectors.kafka.MessageSender
import org.apache.avro.specific.SpecificRecord
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@Timeout(30, unit = TimeUnit.SECONDS)
internal class WikipediaConnectorTest {

    @Test
    fun `receives change event from Wikipedia SSE stream`() {
        val receivedMessages = CopyOnWriteArrayList<Pair<String, WikipediaChange>>()
        val messageLatch = CountDownLatch(1)

        val mockSender = object : MessageSender {
            override fun send(key: String, value: SpecificRecord) {
                receivedMessages.add(key to value as WikipediaChange)
                messageLatch.countDown()
            }

            override fun close() {}
        }

        val connector = WikipediaConnector(emptyList(), mockSender)
        connector.start()

        val received = messageLatch.await(20, TimeUnit.SECONDS)
        connector.close()

        assertThat(received).withFailMessage("Did not receive change event within 20 seconds").isTrue()
        assertThat(receivedMessages).isNotEmpty()

        val (key, change) = receivedMessages.first()
        assertThat(key).isNotBlank()
        assertThat(change.wiki).isNotBlank()
        assertThat(change.title).isNotBlank()
        assertThat(change.user).isNotBlank()
        assertThat(change.type).isNotBlank()
    }

    @Test
    fun `filters by wiki when specified`() {
        val receivedMessages = CopyOnWriteArrayList<Pair<String, WikipediaChange>>()
        val messageLatch = CountDownLatch(3)

        val mockSender = object : MessageSender {
            override fun send(key: String, value: SpecificRecord) {
                receivedMessages.add(key to value as WikipediaChange)
                messageLatch.countDown()
            }

            override fun close() {}
        }

        val connector = WikipediaConnector(listOf("enwiki"), mockSender)
        connector.start()

        val received = messageLatch.await(20, TimeUnit.SECONDS)
        connector.close()

        assertThat(received).withFailMessage("Did not receive 3 filtered events within 20 seconds").isTrue()
        assertThat(receivedMessages).allSatisfy { (_, change) ->
            assertThat(change.wiki).isEqualTo("enwiki")
        }
    }
}
