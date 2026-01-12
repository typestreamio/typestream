package io.typestream.geoip

import io.typestream.compiler.node.Node
import io.typestream.compiler.types.DataStream
import io.typestream.compiler.types.schema.Schema
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

internal class GeoIpExecutionTest {

    private val testSchema = Schema.Struct(
        listOf(
            Schema.Field("id", Schema.String("123")),
            Schema.Field("ip_address", Schema.String("8.8.8.8")),
            Schema.Field("name", Schema.String("test"))
        )
    )

    private val testDataStream = DataStream("/test/topic", testSchema)

    @Nested
    inner class ApplyToShell {

        @Test
        fun `adds country code field to datastream`() {
            val geoIpService = TestGeoIpService(mapOf("8.8.8.8" to "US"))
            val node = Node.GeoIp("geoip-1", "ip_address", "country_code")

            val result = GeoIpExecution.applyToShell(node, listOf(testDataStream), geoIpService)

            assertThat(result).hasSize(1)
            val outputSchema = result[0].schema as Schema.Struct
            val fieldNames = outputSchema.value.map { it.name }
            assertThat(fieldNames).contains("country_code")
        }

        @Test
        fun `sets country code value from lookup`() {
            val geoIpService = TestGeoIpService(mapOf("8.8.8.8" to "US"))
            val node = Node.GeoIp("geoip-1", "ip_address", "country_code")

            val result = GeoIpExecution.applyToShell(node, listOf(testDataStream), geoIpService)

            val outputSchema = result[0].schema as Schema.Struct
            val countryField = outputSchema.value.find { it.name == "country_code" }
            assertThat(countryField?.value).isEqualTo(Schema.String("US"))
        }

        @Test
        fun `sets UNKNOWN when lookup returns null`() {
            val geoIpService = TestGeoIpService(emptyMap())
            val node = Node.GeoIp("geoip-1", "ip_address", "country_code")

            val result = GeoIpExecution.applyToShell(node, listOf(testDataStream), geoIpService)

            val outputSchema = result[0].schema as Schema.Struct
            val countryField = outputSchema.value.find { it.name == "country_code" }
            assertThat(countryField?.value).isEqualTo(Schema.String("UNKNOWN"))
        }

        @Test
        fun `handles empty IP field gracefully`() {
            val emptyIpSchema = Schema.Struct(
                listOf(
                    Schema.Field("id", Schema.String("123")),
                    Schema.Field("ip_address", Schema.String("")),
                )
            )
            val emptyIpDataStream = DataStream("/test/topic", emptyIpSchema)
            val geoIpService = TestGeoIpService(emptyMap())
            val node = Node.GeoIp("geoip-1", "ip_address", "country_code")

            val result = GeoIpExecution.applyToShell(node, listOf(emptyIpDataStream), geoIpService)

            val outputSchema = result[0].schema as Schema.Struct
            val countryField = outputSchema.value.find { it.name == "country_code" }
            assertThat(countryField?.value).isEqualTo(Schema.String("UNKNOWN"))
        }

        @Test
        fun `processes multiple datastreams`() {
            val geoIpService = TestGeoIpService(mapOf("8.8.8.8" to "US", "1.1.1.1" to "AU"))
            val stream1 = DataStream(
                "/test/topic",
                Schema.Struct(listOf(Schema.Field("ip_address", Schema.String("8.8.8.8"))))
            )
            val stream2 = DataStream(
                "/test/topic",
                Schema.Struct(listOf(Schema.Field("ip_address", Schema.String("1.1.1.1"))))
            )
            val node = Node.GeoIp("geoip-1", "ip_address", "country")

            val result = GeoIpExecution.applyToShell(node, listOf(stream1, stream2), geoIpService)

            assertThat(result).hasSize(2)
            val country1 = (result[0].schema as Schema.Struct).value.find { it.name == "country" }?.value
            val country2 = (result[1].schema as Schema.Struct).value.find { it.name == "country" }?.value
            assertThat(country1).isEqualTo(Schema.String("US"))
            assertThat(country2).isEqualTo(Schema.String("AU"))
        }

        @Test
        fun `uses custom output field name`() {
            val geoIpService = TestGeoIpService(mapOf("8.8.8.8" to "US"))
            val node = Node.GeoIp("geoip-1", "ip_address", "geo_country")

            val result = GeoIpExecution.applyToShell(node, listOf(testDataStream), geoIpService)

            val outputSchema = result[0].schema as Schema.Struct
            val fieldNames = outputSchema.value.map { it.name }
            assertThat(fieldNames).contains("geo_country")
            assertThat(fieldNames).doesNotContain("country_code")
        }
    }

    /**
     * Test implementation of GeoIpService that returns predictable results.
     */
    private class TestGeoIpService(private val lookupTable: Map<String, String>) : GeoIpService("/nonexistent") {
        override fun lookup(ipAddress: String): String? = lookupTable[ipAddress]
    }
}
