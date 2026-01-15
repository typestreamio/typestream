package io.typestream.textextractor

import io.typestream.compiler.node.Node
import io.typestream.compiler.types.DataStream
import io.typestream.compiler.types.schema.Schema
import org.apache.kafka.streams.kstream.KStream

/**
 * Execution strategies for TextExtractor node on different runtimes.
 */
object TextExtractorExecution {

    /**
     * Apply TextExtractor transformation to a list of DataStreams (Shell runtime).
     *
     * @param node The TextExtractor node configuration
     * @param dataStreams Input data streams to transform
     * @param textExtractorService Service for text extraction
     * @return Transformed data streams with extracted text field added
     */
    fun applyToShell(
        node: Node.TextExtractor,
        dataStreams: List<DataStream>,
        textExtractorService: TextExtractorService
    ): List<DataStream> {
        return dataStreams.map { ds ->
            val filePath = ds.selectFieldAsString(node.filePathField) ?: ""
            val extractedText = textExtractorService.extract(filePath) ?: ""
            ds.addField(node.outputField, Schema.String(extractedText))
        }
    }

    /**
     * Apply TextExtractor transformation to a Kafka stream.
     *
     * @param node The TextExtractor node configuration
     * @param stream Input Kafka stream to transform
     * @param textExtractorService Service for text extraction
     * @return Transformed stream with extracted text field added to each record
     */
    fun applyToKafka(
        node: Node.TextExtractor,
        stream: KStream<DataStream, DataStream>,
        textExtractorService: TextExtractorService
    ): KStream<DataStream, DataStream> {
        return stream.mapValues { value ->
            val filePath = value.selectFieldAsString(node.filePathField) ?: ""
            val text = textExtractorService.extract(filePath) ?: ""
            value.addField(node.outputField, Schema.String(text))
        }
    }
}
