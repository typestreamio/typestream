package io.typestream.connectors.webvisits.geo

import io.github.oshai.kotlinlogging.KotlinLogging
import org.apache.commons.net.util.SubnetUtils

private val logger = KotlinLogging.logger {}

data class CidrBlock(
    val network: String,
    val lowAddress: Long,
    val highAddress: Long,
    val size: Long
)

class CidrRegistry(countries: List<String> = COUNTRIES) {
    private val countryBlocks: Map<String, List<CidrBlock>>

    init {
        countryBlocks = countries.associateWith { country ->
            loadCidrBlocks(country.lowercase())
        }
        logger.info { "Loaded CIDR blocks for ${countryBlocks.size} countries" }
    }

    private fun loadCidrBlocks(countryCode: String): List<CidrBlock> {
        val resource = javaClass.getResourceAsStream("/cidr/${countryCode}.cidr")
            ?: throw IllegalArgumentException("No CIDR data for country: $countryCode")

        val blocks = resource.bufferedReader().useLines { lines ->
            lines
                .map { it.trim() }
                .filter { it.isNotEmpty() && !it.startsWith("#") }
                .mapNotNull { parseCidr(it) }
                .toList()
        }

        logger.debug { "Loaded ${blocks.size} CIDR blocks for $countryCode" }
        return blocks
    }

    private fun parseCidr(cidr: String): CidrBlock? {
        return try {
            val subnet = SubnetUtils(cidr).apply { isInclusiveHostCount = true }
            val info = subnet.info
            CidrBlock(
                network = cidr,
                lowAddress = ipToLong(info.lowAddress),
                highAddress = ipToLong(info.highAddress),
                size = info.addressCountLong
            )
        } catch (e: Exception) {
            logger.warn { "Failed to parse CIDR block: $cidr - ${e.message}" }
            null
        }
    }

    fun getBlocksForCountry(countryCode: String): List<CidrBlock> {
        return countryBlocks[countryCode.uppercase()]
            ?: throw IllegalArgumentException("Unknown country: $countryCode")
    }

    fun getAvailableCountries(): Set<String> = countryBlocks.keys

    companion object {
        val COUNTRIES = listOf(
            "US", "GB", "DE", "FR", "CA", "AU", "BR", "IN", "JP", "CN",
            "ES", "IT", "NL", "SE", "SG"
        )

        fun ipToLong(ip: String): Long {
            return ip.split(".").fold(0L) { acc, octet ->
                (acc shl 8) + octet.toLong()
            }
        }

        fun longToIp(value: Long): String {
            return listOf(
                (value shr 24) and 0xFF,
                (value shr 16) and 0xFF,
                (value shr 8) and 0xFF,
                value and 0xFF
            ).joinToString(".")
        }
    }
}
