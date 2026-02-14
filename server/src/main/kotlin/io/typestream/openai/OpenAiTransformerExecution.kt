package io.typestream.openai

import io.typestream.compiler.node.NodeOpenAiTransformer
import io.typestream.compiler.types.DataStream
import io.typestream.compiler.types.schema.Schema
import org.apache.kafka.streams.kstream.KStream

/**
 * Execution strategies for OpenAiTransformer node on different runtimes.
 */
object OpenAiTransformerExecution {

    /**
     * Apply OpenAiTransformer transformation to a list of DataStreams (Shell runtime).
     *
     * @param node The OpenAiTransformer node configuration
     * @param dataStreams Input data streams to transform
     * @param openAiService Service for OpenAI API calls
     * @return Transformed data streams with AI response field added
     */
    fun applyToShell(
        node: NodeOpenAiTransformer,
        dataStreams: List<DataStream>,
        openAiService: OpenAiService
    ): List<DataStream> {
        return dataStreams.map { ds ->
            val messageJson = ds.schema.toJsonElement().toString()
            val response = openAiService.complete(node.prompt, messageJson, node.model) ?: ""
            ds.addField(node.outputField, Schema.String(response))
        }
    }

    /**
     * Apply OpenAiTransformer transformation to a Kafka stream.
     *
     * @param node The OpenAiTransformer node configuration
     * @param stream Input Kafka stream to transform
     * @param openAiService Service for OpenAI API calls
     * @return Transformed stream with AI response field added to each record
     */
    fun applyToKafka(
        node: NodeOpenAiTransformer,
        stream: KStream<DataStream, DataStream>,
        openAiService: OpenAiService
    ): KStream<DataStream, DataStream> {
        return stream.mapValues { value ->
            val messageJson = value.schema.toJsonElement().toString()
            val response = openAiService.complete(node.prompt, messageJson, node.model) ?: ""
            value.addField(node.outputField, Schema.String(response))
        }
    }
}
