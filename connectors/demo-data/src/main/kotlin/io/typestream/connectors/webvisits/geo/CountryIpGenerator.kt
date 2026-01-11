package io.typestream.connectors.webvisits.geo

import kotlin.random.Random

data class CountryWeight(val code: String, val weight: Double)

class CountryIpGenerator(
    private val registry: CidrRegistry = CidrRegistry(),
    weights: List<CountryWeight> = DEFAULT_WEIGHTS
) {
    private val random = Random.Default
    private val weightedCountries: List<Pair<String, Double>>

    init {
        // Normalize weights and create cumulative distribution
        val total = weights.sumOf { it.weight }
        var cumulative = 0.0
        weightedCountries = weights.map { w ->
            cumulative += w.weight / total
            w.code to cumulative
        }
    }

    /**
     * Generate a random IP address with its country code.
     * @return Pair of (countryCode, ipAddress)
     */
    fun generate(): Pair<String, String> {
        val country = selectCountry()
        val ip = generateIpForCountry(country)
        return country to ip
    }

    /**
     * Generate a random IP address for a specific country.
     */
    fun generateForCountry(countryCode: String): String {
        return generateIpForCountry(countryCode.uppercase())
    }

    private fun selectCountry(): String {
        val r = random.nextDouble()
        return weightedCountries.first { it.second >= r }.first
    }

    private fun generateIpForCountry(countryCode: String): String {
        val blocks = registry.getBlocksForCountry(countryCode)

        // Weight block selection by size for uniform distribution across IP space
        val totalSize = blocks.sumOf { it.size }
        val target = random.nextLong(totalSize)

        var cumulative = 0L
        val block = blocks.first { b ->
            cumulative += b.size
            cumulative > target
        }

        // Generate random IP within the block
        val range = block.highAddress - block.lowAddress
        val offset = if (range > 0) random.nextLong(range + 1) else 0L
        return CidrRegistry.longToIp(block.lowAddress + offset)
    }

    companion object {
        val DEFAULT_WEIGHTS = listOf(
            CountryWeight("US", 35.0),
            CountryWeight("GB", 10.0),
            CountryWeight("DE", 8.0),
            CountryWeight("FR", 6.0),
            CountryWeight("CA", 5.0),
            CountryWeight("AU", 4.0),
            CountryWeight("BR", 4.0),
            CountryWeight("IN", 6.0),
            CountryWeight("JP", 5.0),
            CountryWeight("CN", 5.0),
            CountryWeight("ES", 3.0),
            CountryWeight("IT", 3.0),
            CountryWeight("NL", 2.0),
            CountryWeight("SE", 2.0),
            CountryWeight("SG", 2.0)
        )
    }
}
