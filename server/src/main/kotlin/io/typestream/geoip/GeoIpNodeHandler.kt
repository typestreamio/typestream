package io.typestream.geoip

import io.typestream.compiler.node.Node
import io.typestream.grpc.job_service.Job

/**
 * Handler for GeoIP pipeline nodes.
 * Centralizes proto-to-node conversion for GeoIP transformation.
 *
 * Note: Schema inference is now handled by Node.GeoIp.inferOutputSchema().
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
}
