package io.typestream.pipeline

import com.google.protobuf.util.JsonFormat
import io.typestream.grpc.job_service.Job.PipelineGraph
import io.typestream.grpc.pipeline_service.Pipeline.PipelineMetadata
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

data class PipelineFile(
    val metadata: PipelineMetadata,
    val graph: PipelineGraph
)

object PipelineFileParser {

    private val json = Json { ignoreUnknownKeys = true }
    private val protoJsonParser = JsonFormat.parser().ignoringUnknownFields()

    fun parse(content: String): PipelineFile {
        val root = json.parseToJsonElement(content).jsonObject

        val name = root["name"]?.jsonPrimitive?.content
            ?: error("Pipeline file must have a 'name' field")
        val version = root["version"]?.jsonPrimitive?.content ?: "1"
        val description = root["description"]?.jsonPrimitive?.content ?: ""

        val graphElement = root["graph"]
            ?: error("Pipeline file must have a 'graph' field")
        require(graphElement is JsonObject) { "'graph' must be a JSON object" }

        val graphBuilder = PipelineGraph.newBuilder()
        protoJsonParser.merge(graphElement.toString(), graphBuilder)

        val metadata = PipelineMetadata.newBuilder()
            .setName(name)
            .setVersion(version)
            .setDescription(description)
            .build()

        return PipelineFile(
            metadata = metadata,
            graph = graphBuilder.build()
        )
    }
}
