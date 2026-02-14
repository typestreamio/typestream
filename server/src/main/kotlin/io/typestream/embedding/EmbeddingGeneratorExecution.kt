package io.typestream.embedding

import io.typestream.compiler.node.NodeEmbeddingGenerator
import io.typestream.compiler.types.DataStream
import io.typestream.compiler.types.schema.Schema
import org.apache.kafka.streams.kstream.KStream

/**
 * Execution strategies for EmbeddingGenerator node on different runtimes.
 */
object EmbeddingGeneratorExecution {

    /**
     * Apply EmbeddingGenerator transformation to a list of DataStreams (Shell runtime).
     *
     * @param node The EmbeddingGenerator node configuration
     * @param dataStreams Input data streams to transform
     * @param embeddingService Service for embedding generation
     * @return Transformed data streams with embedding vector field added
     */
    fun applyToShell(
        node: NodeEmbeddingGenerator,
        dataStreams: List<DataStream>,
        embeddingService: EmbeddingGeneratorService
    ): List<DataStream> {
        return dataStreams.map { ds ->
            val text = ds.selectFieldAsString(node.textField) ?: ""
            val embedding = embeddingService.embed(text, node.model) ?: emptyList()
            val embeddingSchema = Schema.List(
                embedding.map { Schema.Float(it) },
                Schema.Float(0.0f)
            )
            ds.addField(node.outputField, embeddingSchema)
        }
    }

    /**
     * Apply EmbeddingGenerator transformation to a Kafka stream.
     *
     * @param node The EmbeddingGenerator node configuration
     * @param stream Input Kafka stream to transform
     * @param embeddingService Service for embedding generation
     * @return Transformed stream with embedding vector field added to each record
     */
    fun applyToKafka(
        node: NodeEmbeddingGenerator,
        stream: KStream<DataStream, DataStream>,
        embeddingService: EmbeddingGeneratorService
    ): KStream<DataStream, DataStream> {
        return stream.mapValues { value ->
            val text = value.selectFieldAsString(node.textField) ?: ""
            val embedding = embeddingService.embed(text, node.model) ?: emptyList()
            val embeddingSchema = Schema.List(
                embedding.map { Schema.Float(it) },
                Schema.Float(0.0f)
            )
            value.addField(node.outputField, embeddingSchema)
        }
    }
}
