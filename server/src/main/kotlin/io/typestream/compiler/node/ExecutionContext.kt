package io.typestream.compiler.node

import io.typestream.embedding.EmbeddingGeneratorService
import io.typestream.geoip.GeoIpService
import io.typestream.openai.OpenAiService
import io.typestream.textextractor.TextExtractorService

data class ExecutionContext(
    val geoIpService: GeoIpService? = null,
    val textExtractorService: TextExtractorService? = null,
    val embeddingGeneratorService: EmbeddingGeneratorService? = null,
    val openAiService: OpenAiService? = null
)
