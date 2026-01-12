package io.typestream.geoip

import io.typestream.compiler.node.Node
import io.typestream.compiler.types.DataStream
import io.typestream.compiler.types.schema.Schema
import org.apache.kafka.streams.kstream.KStream

/**
 * Execution strategies for GeoIP node on different runtimes.
 *
 * This is a reference implementation for the composable node architecture.
 * See docs/docs/design/composable-nodes.md for the full pattern.
 */
object GeoIpExecution {

    /**
     * Apply GeoIP transformation to a list of DataStreams (Shell runtime).
     *
     * @param node The GeoIP node configuration
     * @param dataStreams Input data streams to transform
     * @param geoIpService Service for IP-to-country lookup
     * @return Transformed data streams with country code field added
     */
    fun applyToShell(
        node: Node.GeoIp,
        dataStreams: List<DataStream>,
        geoIpService: GeoIpService
    ): List<DataStream> {
        return dataStreams.map { ds ->
            val ipValue = ds.selectFieldAsString(node.ipField) ?: ""
            val countryCode = geoIpService.lookup(ipValue) ?: "UNKNOWN"
            ds.addField(node.outputField, Schema.String(countryCode))
        }
    }

    /**
     * Apply GeoIP transformation to a Kafka stream.
     *
     * @param node The GeoIP node configuration
     * @param stream Input Kafka stream to transform
     * @param geoIpService Service for IP-to-country lookup
     * @return Transformed stream with country code field added to each record
     */
    fun applyToKafka(
        node: Node.GeoIp,
        stream: KStream<DataStream, DataStream>,
        geoIpService: GeoIpService
    ): KStream<DataStream, DataStream> {
        return stream.mapValues { value ->
            val ip = value.selectFieldAsString(node.ipField) ?: ""
            val country = geoIpService.lookup(ip) ?: "UNKNOWN"
            value.addField(node.outputField, Schema.String(country))
        }
    }
}
