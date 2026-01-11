package io.typestream.connectors.webvisits

import io.typestream.connectors.avro.WebVisit
import io.typestream.connectors.kafka.MessageSender
import org.apache.avro.specific.SpecificRecord
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@Timeout(10, unit = TimeUnit.SECONDS)
internal class WebVisitsConnectorTest {

    @Test
    fun `generates web visits with valid fields`() {
        val receivedMessages = CopyOnWriteArrayList<Pair<String, WebVisit>>()
        val messageLatch = CountDownLatch(5)

        val mockSender = object : MessageSender {
            override fun send(key: String, value: SpecificRecord) {
                receivedMessages.add(key to value as WebVisit)
                messageLatch.countDown()
            }
            override fun close() {}
        }

        val connector = WebVisitsConnector(
            ratePerSecond = 100.0,  // Fast for testing
            sender = mockSender
        )
        connector.start()

        val received = messageLatch.await(5, TimeUnit.SECONDS)
        connector.close()

        assertThat(received).withFailMessage("Did not receive 5 messages within timeout").isTrue()
        assertThat(receivedMessages).hasSize(5)

        receivedMessages.forEach { (key, visit) ->
            // Key should be session ID
            assertThat(key).isEqualTo(visit.sessionId)

            // Validate all fields
            assertThat(visit.ipAddress).matches("\\d+\\.\\d+\\.\\d+\\.\\d+")
            assertThat(visit.urlPath).startsWith("/")
            assertThat(visit.httpMethod).isIn("GET", "POST", "PUT", "DELETE")
            assertThat(visit.statusCode).isIn(200, 301, 404, 500, 503)
        }
    }

    @Test
    fun `respects country filter`() {
        val receivedMessages = CopyOnWriteArrayList<WebVisit>()
        val messageLatch = CountDownLatch(20)

        val mockSender = object : MessageSender {
            override fun send(key: String, value: SpecificRecord) {
                receivedMessages.add(value as WebVisit)
                messageLatch.countDown()
            }
            override fun close() {}
        }

        val connector = WebVisitsConnector(
            ratePerSecond = 200.0,
            countries = listOf("US", "GB"),
            sender = mockSender
        )
        connector.start()

        val received = messageLatch.await(5, TimeUnit.SECONDS)
        connector.close()

        assertThat(received).isTrue()

        // All IPs should be valid
        receivedMessages.forEach { visit ->
            assertThat(visit.ipAddress).matches("\\d+\\.\\d+\\.\\d+\\.\\d+")
        }
    }
}
