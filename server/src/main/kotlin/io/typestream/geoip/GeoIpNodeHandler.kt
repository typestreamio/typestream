package io.typestream.geoip

import io.typestream.compiler.node.Node
import io.typestream.compiler.types.DataStream
import io.typestream.compiler.types.TypeRules
import io.typestream.grpc.job_service.Job

/**
 * Handler for GeoIP pipeline nodes.
 * Centralizes proto-to-node conversion and type inference for GeoIP transformation.
 *
 * This is a reference implementation for the composable node architecture.
 * See docs/docs/design/composable-nodes.md for the full pattern.
 */
object GeoIpNodeHandler {

    /**
     * Check if this handler can process the given proto node.
     */
    fun canHandle(proto: Job.PipelineNode): Boolean = proto.hasGeoIp()

    /**
     * Convert GeoIP proto to internal Node type.
     */
    fun fromProto(proto: Job.PipelineNode): Node.GeoIp {
        require(proto.hasGeoIp()) { "Expected GeoIp node, got: ${proto.nodeTypeCase}" }
        val g = proto.geoIp
        return Node.GeoIp(proto.id, g.ipField, g.outputField)
    }

    /**
     * Infer the output schema for a GeoIP node.
     * Adds a string field (country code) to the input schema.
     *
     * @param input The input DataStream schema
     * @param ipField Name of the field containing the IP address to lookup
     * @param outputField Name of the field to add (default: "country_code")
     * @return DataStream with the new field added
     * @throws IllegalArgumentException if ipField doesn't exist in the input schema
     */
    fun inferType(input: DataStream, ipField: String, outputField: String): DataStream {
        return TypeRules.inferGeoIp(input, ipField, outputField)
    }
}
