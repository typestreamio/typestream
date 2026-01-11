package io.typestream.connectors.webvisits.geo

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class CountryIpGeneratorTest {

    @Test
    fun `generates valid IP addresses for each country`() {
        val registry = CidrRegistry()
        val generator = CountryIpGenerator(registry)

        CidrRegistry.COUNTRIES.forEach { country ->
            val ip = generator.generateForCountry(country)
            assertThat(ip)
                .`as`("IP for $country")
                .matches("\\d+\\.\\d+\\.\\d+\\.\\d+")

            // Verify IP octets are in valid range
            val parts = ip.split(".").map { it.toInt() }
            parts.forEach { octet ->
                assertThat(octet).isBetween(0, 255)
            }
        }
    }

    @Test
    fun `weighted distribution favors US`() {
        val registry = CidrRegistry()
        val generator = CountryIpGenerator(registry)

        val countryCounts = mutableMapOf<String, Int>()
        repeat(1000) {
            val (country, _) = generator.generate()
            countryCounts.merge(country, 1, Int::plus)
        }

        // US should have significantly more than others (weight 35% = ~350)
        assertThat(countryCounts["US"]).isGreaterThan(200)

        // All countries should have some representation
        CidrRegistry.COUNTRIES.forEach { country ->
            assertThat(countryCounts[country])
                .`as`("Count for $country")
                .isNotNull()
                .isGreaterThan(0)
        }
    }

    @Test
    fun `can use custom country subset`() {
        val countries = listOf("US", "GB", "DE")
        val registry = CidrRegistry(countries)
        val weights = countries.map { CountryWeight(it, 1.0) }
        val generator = CountryIpGenerator(registry, weights)

        repeat(100) {
            val (country, ip) = generator.generate()
            assertThat(country).isIn("US", "GB", "DE")
            assertThat(ip).matches("\\d+\\.\\d+\\.\\d+\\.\\d+")
        }
    }
}
