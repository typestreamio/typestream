package io.typestream.connectors.webvisits

import io.typestream.connectors.avro.WebVisit
import io.typestream.connectors.webvisits.geo.CountryIpGenerator
import net.datafaker.Faker
import java.time.Instant
import java.util.UUID
import kotlin.random.Random

class WebVisitGenerator(
    private val ipGenerator: CountryIpGenerator = CountryIpGenerator()
) {
    private val faker = Faker()
    private val random = Random.Default

    // Simulate active sessions (session_id -> context)
    private val activeSessions = mutableMapOf<String, SessionContext>()

    fun generate(): WebVisit {
        val (_, ipAddress) = ipGenerator.generate()
        val session = getOrCreateSession(ipAddress)
        val isBot = random.nextDouble() < 0.05 // 5% bot traffic

        return WebVisit.newBuilder()
            .setIpAddress(ipAddress)
            .setTimestamp(Instant.now())
            .setUrlPath(generateUrlPath(session))
            .setHttpMethod(generateHttpMethod())
            .setStatusCode(generateStatusCode())
            .setResponseBytes(random.nextLong(500, 500_000))
            .setUserAgent(if (isBot) generateBotUserAgent() else session.userAgent)
            .setReferrer(generateReferrer(session))
            .setSessionId(session.id)
            .setPageLoadTimeMs(random.nextInt(50, 3000))
            .setIsBot(isBot)
            .setDeviceType(session.deviceType)
            .setBrowser(session.browser)
            .setOs(session.os)
            .build()
    }

    private fun getOrCreateSession(ipAddress: String): SessionContext {
        // 30% chance of returning visitor, 70% new session
        if (random.nextDouble() < 0.3 && activeSessions.isNotEmpty()) {
            val existingSession = activeSessions.entries.random().value
            existingSession.visitCount++
            existingSession.lastVisit = Instant.now()
            return existingSession
        }

        // Expire old sessions (keep max 1000)
        if (activeSessions.size > 1000) {
            val toRemove = activeSessions.entries
                .sortedBy { it.value.lastVisit }
                .take(100)
                .map { it.key }
            toRemove.forEach { activeSessions.remove(it) }
        }

        val session = createNewSession()
        activeSessions[session.id] = session
        return session
    }

    private fun createNewSession(): SessionContext {
        val deviceType = DEVICE_TYPES.random(random)
        val (browser, os) = when (deviceType) {
            "mobile" -> MOBILE_BROWSERS.random(random) to MOBILE_OS.random(random)
            "tablet" -> MOBILE_BROWSERS.random(random) to MOBILE_OS.random(random)
            else -> DESKTOP_BROWSERS.random(random) to DESKTOP_OS.random(random)
        }

        return SessionContext(
            id = UUID.randomUUID().toString(),
            userAgent = generateUserAgent(browser, os, deviceType),
            deviceType = deviceType,
            browser = browser,
            os = os,
            visitCount = 1,
            lastVisit = Instant.now(),
            entryPath = POPULAR_PATHS.random(random)
        )
    }

    private fun generateUserAgent(browser: String, os: String, deviceType: String): String {
        val osVersion = when (os) {
            "Windows" -> "Windows NT 10.0; Win64; x64"
            "macOS" -> "Macintosh; Intel Mac OS X 10_15_7"
            "Linux" -> "X11; Linux x86_64"
            "Android" -> "Linux; Android ${random.nextInt(10, 15)}"
            "iOS" -> "iPhone; CPU iPhone OS ${random.nextInt(14, 18)}_0 like Mac OS X"
            else -> os
        }

        val browserVersion = when (browser) {
            "Chrome", "Chrome Mobile" -> "Chrome/${random.nextInt(100, 130)}.0.0.0"
            "Firefox" -> "Firefox/${random.nextInt(100, 130)}.0"
            "Safari", "Safari Mobile" -> "Safari/605.1.15"
            "Edge" -> "Edg/${random.nextInt(100, 130)}.0.0.0"
            "Samsung Internet" -> "SamsungBrowser/${random.nextInt(18, 25)}.0"
            else -> browser
        }

        return "Mozilla/5.0 ($osVersion) AppleWebKit/537.36 (KHTML, like Gecko) $browserVersion"
    }

    private fun generateUrlPath(session: SessionContext): String {
        return if (session.visitCount == 1) {
            session.entryPath // First visit goes to entry page
        } else {
            // Navigate to related pages
            when (random.nextInt(10)) {
                in 0..3 -> POPULAR_PATHS.random(random)
                in 4..6 -> "/products/${random.nextInt(1, 1000)}"
                in 7..8 -> "/blog/${faker.lorem().word()}-${random.nextInt(1, 500)}"
                else -> "/search?q=${faker.commerce().productName().replace(" ", "+")}"
            }
        }
    }

    private fun generateHttpMethod(): String {
        return when (random.nextInt(100)) {
            in 0..89 -> "GET"
            in 90..96 -> "POST"
            in 97..98 -> "PUT"
            else -> "DELETE"
        }
    }

    private fun generateStatusCode(): Int {
        return when (random.nextInt(100)) {
            in 0..94 -> 200
            in 95..96 -> 301
            97 -> 404
            98 -> 500
            else -> 503
        }
    }

    private fun generateReferrer(session: SessionContext): String? {
        if (session.visitCount > 1) {
            // Internal referrer for return visits
            return "https://example.com${session.entryPath}"
        }

        return when (random.nextInt(10)) {
            in 0..3 -> null // Direct traffic
            in 4..6 -> "https://www.google.com/search?q=${faker.commerce().productName()}"
            7 -> "https://www.facebook.com/"
            8 -> "https://twitter.com/"
            else -> "https://${faker.internet().domainName()}/"
        }
    }

    private fun generateBotUserAgent(): String {
        return BOT_USER_AGENTS.random(random)
    }

    private data class SessionContext(
        val id: String,
        val userAgent: String,
        val deviceType: String,
        val browser: String,
        val os: String,
        var visitCount: Int,
        var lastVisit: Instant,
        val entryPath: String
    )

    companion object {
        private val DEVICE_TYPES = listOf("desktop", "desktop", "desktop", "mobile", "mobile", "tablet")
        private val DESKTOP_BROWSERS = listOf("Chrome", "Firefox", "Safari", "Edge")
        private val MOBILE_BROWSERS = listOf("Chrome Mobile", "Safari Mobile", "Samsung Internet")
        private val DESKTOP_OS = listOf("Windows", "macOS", "Linux")
        private val MOBILE_OS = listOf("Android", "iOS")

        private val POPULAR_PATHS = listOf(
            "/", "/products", "/about", "/contact", "/pricing",
            "/blog", "/docs", "/login", "/signup", "/cart"
        )

        private val BOT_USER_AGENTS = listOf(
            "Googlebot/2.1 (+http://www.google.com/bot.html)",
            "Mozilla/5.0 (compatible; Bingbot/2.0; +http://www.bing.com/bingbot.htm)",
            "Mozilla/5.0 (compatible; YandexBot/3.0; +http://yandex.com/bots)",
            "Slackbot-LinkExpanding 1.0 (+https://api.slack.com/robots)",
            "facebookexternalhit/1.1 (+http://www.facebook.com/externalhit_uatext.php)"
        )
    }
}
