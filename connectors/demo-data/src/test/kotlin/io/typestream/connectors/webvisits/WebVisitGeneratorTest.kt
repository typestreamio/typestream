package io.typestream.connectors.webvisits

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class WebVisitGeneratorTest {

    @Test
    fun `generates web visits with all required fields`() {
        val generator = WebVisitGenerator()

        repeat(10) {
            val visit = generator.generate()

            assertThat(visit.ipAddress).matches("\\d+\\.\\d+\\.\\d+\\.\\d+")
            assertThat(visit.timestamp).isNotNull()
            assertThat(visit.urlPath).startsWith("/")
            assertThat(visit.httpMethod).isIn("GET", "POST", "PUT", "DELETE")
            assertThat(visit.statusCode).isIn(200, 301, 404, 500, 503)
            assertThat(visit.responseBytes).isBetween(500L, 500_000L)
            assertThat(visit.userAgent).isNotBlank()
            assertThat(visit.sessionId).isNotBlank()
            assertThat(visit.pageLoadTimeMs).isBetween(50, 3000)
            assertThat(visit.deviceType).isIn("desktop", "mobile", "tablet")
            assertThat(visit.browser).isNotBlank()
            assertThat(visit.os).isNotBlank()
        }
    }

    @Test
    fun `generates realistic distribution of HTTP methods`() {
        val generator = WebVisitGenerator()
        val methodCounts = mutableMapOf<String, Int>()

        repeat(1000) {
            val visit = generator.generate()
            methodCounts.merge(visit.httpMethod, 1, Int::plus)
        }

        // GET should be most common (~90%)
        assertThat(methodCounts["GET"]).isGreaterThan(800)
        // POST should be second (~7%)
        assertThat(methodCounts["POST"]).isBetween(30, 150)
    }

    @Test
    fun `includes bot traffic`() {
        val generator = WebVisitGenerator()
        var botCount = 0

        repeat(500) {
            val visit = generator.generate()
            if (visit.isBot) {
                botCount++
                assertThat(visit.userAgent).containsAnyOf("bot", "Bot", "Slack", "facebook")
            }
        }

        // ~5% should be bots, so with 500 samples, expect 15-40
        assertThat(botCount).isBetween(5, 60)
    }

    @Test
    fun `returns sessions for repeat visitors`() {
        val generator = WebVisitGenerator()
        val sessions = mutableSetOf<String>()

        repeat(100) {
            val visit = generator.generate()
            sessions.add(visit.sessionId)
        }

        // With 30% returning visitors, should have fewer unique sessions than visits
        assertThat(sessions.size).isLessThan(100)
        assertThat(sessions.size).isGreaterThan(50) // But not too few
    }
}
