package io.typestream.compiler.kafka

import io.typestream.compiler.node.KeyValue
import io.typestream.compiler.node.Node
import io.typestream.compiler.types.DataStream
import io.typestream.compiler.types.Encoding
import io.typestream.compiler.types.schema.Schema
import io.typestream.compiler.types.datastream.fromAvroGenericRecord
import io.typestream.compiler.types.datastream.fromBytes
import io.typestream.compiler.types.datastream.fromKeyBytes
import io.typestream.compiler.types.datastream.fromProtoMessage
import io.typestream.compiler.types.datastream.join
import io.typestream.compiler.types.datastream.toAvroGenericRecord
import io.typestream.compiler.types.datastream.toAvroSchema
import io.typestream.compiler.types.datastream.toBytes
import io.typestream.compiler.types.datastream.toProtoMessage
import io.typestream.compiler.types.datastream.toProtoSchema
import io.typestream.embedding.EmbeddingGeneratorExecution
import io.typestream.embedding.EmbeddingGeneratorService
import io.typestream.geoip.GeoIpExecution
import io.typestream.geoip.GeoIpService
import io.typestream.openai.OpenAiService
import io.typestream.openai.OpenAiTransformerExecution
import io.typestream.textextractor.TextExtractorExecution
import io.typestream.textextractor.TextExtractorService
import io.typestream.config.SchemaRegistryConfig
import io.typestream.kafka.avro.AvroSerde
import io.typestream.kafka.ProtoSerde
import io.typestream.kafka.schemaregistry.SchemaRegistryClient
import io.typestream.kafka.StreamsBuilderWrapper
import org.apache.kafka.common.serialization.Serdes
import org.apache.kafka.common.utils.Bytes
import org.apache.kafka.streams.KeyValue.pair
import org.apache.kafka.streams.kstream.Consumed
import org.apache.kafka.streams.kstream.JoinWindows
import org.apache.kafka.streams.kstream.KGroupedStream
import org.apache.kafka.streams.kstream.KStream
import org.apache.kafka.streams.kstream.Materialized
import org.apache.kafka.streams.kstream.Produced
import org.apache.kafka.streams.kstream.TimeWindows
import org.apache.kafka.streams.state.KeyValueStore
import org.apache.kafka.streams.state.WindowStore
import java.time.Duration

data class KafkaStreamSource(
    val node: Node.StreamSource,
    private val streamsBuilder: StreamsBuilderWrapper,
    private val geoIpService: GeoIpService,
    private val textExtractorService: TextExtractorService,
    private val embeddingGeneratorService: EmbeddingGeneratorService,
    private val openAiService: OpenAiService
) {
    private val schemaRegistryClient: SchemaRegistryClient? by lazy {
        try {
            SchemaRegistryClient(SchemaRegistryConfig.fromMap(streamsBuilder.config.toMutableMap()))
        } catch (_: Exception) { null }
    }

    private var stream: KStream<DataStream, DataStream> = initStream()
    private var groupedStream: KGroupedStream<DataStream, DataStream>? = null
    private var countStoreName: String? = null
    private var reduceStoreNames = mutableListOf<String>()

    private fun initStream(): KStream<DataStream, DataStream> {
        var s = stream(node.dataStream)
        if (node.unwrapCdc) {
            s = applyCdcUnwrap(s)
        }
        return s
    }

    /**
     * Unwrap CDC envelope at runtime by:
     * 1. Filtering out DELETE records (where 'after' is null)
     * 2. Extracting fields from 'after' to top level
     */
    private fun applyCdcUnwrap(s: KStream<DataStream, DataStream>): KStream<DataStream, DataStream> {
        return s
            // Filter out DELETE records where 'after' is null
            .filter { _, v ->
                val afterValue = getAfterFieldValue(v.schema)
                afterValue != null
            }
            // Extract 'after' fields to top level
            .mapValues { v ->
                unwrapCdcRecord(v)
            }
    }

    /**
     * Get the value of the 'after' field from a CDC envelope schema.
     * Returns null if the record is a DELETE (after is null).
     */
    private fun getAfterFieldValue(schema: Schema): Schema? {
        if (schema !is Schema.Struct) return null

        val afterField = schema.value.find { it.name == "after" } ?: return null
        val afterValue = afterField.value

        return when (afterValue) {
            is Schema.Struct -> afterValue
            is Schema.Optional -> afterValue.value as? Schema.Struct
            else -> null
        }
    }

    /**
     * Unwrap a CDC record by extracting 'after' fields to top level.
     */
    private fun unwrapCdcRecord(ds: DataStream): DataStream {
        val afterStruct = getAfterFieldValue(ds.schema) as? Schema.Struct ?: return ds
        return DataStream(ds.path, afterStruct)
    }

    fun getCountStoreName(): String? = countStoreName
    fun getReduceStoreNames(): List<String> = reduceStoreNames.toList()

    private fun stream(dataStream: DataStream): KStream<DataStream, DataStream> {
        val config = streamsBuilder.config.toMutableMap()
        return when (node.encoding) {

            Encoding.AVRO -> {
                val valueSerde = AvroSerde(dataStream.toAvroSchema())
                valueSerde.configure(config, false)

                streamsBuilder.stream(
                    dataStream.name, Consumed.with(Serdes.Bytes(), valueSerde)
                ).map { k, v ->
                    pair(
                        DataStream.fromKeyBytes(dataStream.path, k, schemaRegistryClient), DataStream.fromAvroGenericRecord(dataStream.path, v)
                    )
                }
            }

            Encoding.PROTOBUF -> {
                val valueSerde = ProtoSerde(dataStream.toProtoSchema())
                valueSerde.configure(config, false)
                streamsBuilder.stream(
                    dataStream.name, Consumed.with(Serdes.Bytes(), valueSerde)
                ).map { k, v ->
                    pair(
                        DataStream.fromKeyBytes(dataStream.path, k, schemaRegistryClient), DataStream.fromProtoMessage(dataStream.path, v)
                    )
                }
            }

            else -> streamsBuilder.stream(dataStream.name)
        }
    }

    fun to(node: Node.Sink) {
        val config = streamsBuilder.config.toMutableMap()

        when (node.encoding) {
            Encoding.AVRO -> {
                val keySerde = Serdes.Bytes()
                keySerde.configure(config, true)

                val valueSerde = AvroSerde(node.output.toAvroSchema())
                valueSerde.configure(config, false)

                stream.map { k, v ->
                    pair(k.toBytes(), v.toAvroGenericRecord())
                }.to(node.output.name, Produced.with(keySerde, valueSerde))
            }

            Encoding.PROTOBUF -> {
                val keySerde = Serdes.Bytes()
                keySerde.configure(config, true)

                val valueSerde = ProtoSerde(node.output.toProtoSchema())
                valueSerde.configure(config, false)

                stream.map { k, v ->
                    pair(k.toBytes(), v.toProtoMessage())
                }.to(node.output.name, Produced.with(keySerde, valueSerde))
            }

            else -> stream.to(node.output.name)
        }
    }

    fun map(map: Node.Map) {
        stream = stream.map { k, v ->
            val newVal = map.mapper(KeyValue(k, v))
            pair(newVal.key, newVal.value)
        }
    }

    fun each(each: Node.Each) {
        stream.foreach { k, v -> each.fn(KeyValue(k, v)) }
    }

    fun join(join: Node.Join) {
        val windowSize = Duration.ofMinutes(5)
        val advance = Duration.ofMinutes(1)
        val window = JoinWindows.ofTimeDifferenceAndGrace(windowSize, advance)
        val with = stream(join.with)

        stream = stream.join(with, { left, right -> left.join(right) }, window)
    }

    fun filter(filter: Node.Filter) {
        stream = if (filter.byKey) {
            stream.filter { k, _ -> filter.predicate.matches(k) }
        } else {
            stream.filter { _, v -> filter.predicate.matches(v) }
        }
    }

    fun group(group: Node.Group) {
        groupedStream = stream.groupBy { k, v -> group.keyMapper(KeyValue(k, v)) }
    }

    fun count(storeName: String) {
        requireNotNull(groupedStream) { "cannot count a non-grouped stream" }

        countStoreName = storeName
        val materialized = Materialized.`as`<DataStream, Long, KeyValueStore<Bytes, ByteArray>>(storeName)
        stream = groupedStream!!.count(materialized).mapValues { v -> DataStream.fromLong("", v) }.toStream()
    }

    fun windowedCount(storeName: String, windowSize: Duration) {
        requireNotNull(groupedStream) { "cannot count a non-grouped stream" }

        countStoreName = storeName
        val timeWindows = TimeWindows.ofSizeAndGrace(windowSize, Duration.ofSeconds(30))

        val materialized = Materialized.`as`<DataStream, Long, WindowStore<Bytes, ByteArray>>(storeName)
        stream = groupedStream!!
            .windowedBy(timeWindows)
            .count(materialized)
            .toStream()
            .map { windowedKey, count ->
                org.apache.kafka.streams.KeyValue(windowedKey.key(), DataStream.fromLong("", count))
            }
    }

    fun reduceLatest(storeName: String) {
        requireNotNull(groupedStream) { "cannot reduce a non-grouped stream" }

        reduceStoreNames.add(storeName)
        val materialized = Materialized.`as`<DataStream, DataStream, KeyValueStore<Bytes, ByteArray>>(storeName)
        stream = groupedStream!!.reduce(
            { _, newValue -> newValue },  // Keep latest value
            materialized
        ).toStream()
    }

    fun geoIp(geoIp: Node.GeoIp) {
        stream = GeoIpExecution.applyToKafka(geoIp, stream, geoIpService)
    }

    fun textExtract(textExtractor: Node.TextExtractor) {
        stream = TextExtractorExecution.applyToKafka(textExtractor, stream, textExtractorService)
    }

    fun embeddingGenerate(embeddingGenerator: Node.EmbeddingGenerator) {
        stream = EmbeddingGeneratorExecution.applyToKafka(embeddingGenerator, stream, embeddingGeneratorService)
    }

    fun openAiTransform(openAiTransformer: Node.OpenAiTransformer) {
        stream = OpenAiTransformerExecution.applyToKafka(openAiTransformer, stream, openAiService)
    }

    /**
     * Creates a branch in the Kafka Streams topology for preview/inspection.
     * Writes records to a side-channel topic without interrupting downstream flow.
     * The topic is consumed by [KafkaStreamsJob.output] via a real KafkaConsumer,
     * and streamed to the UI via [JobService.streamPreview].
     */
    fun toInspector(inspector: Node.Inspector, programId: String) {
        val inspectTopic = "$programId-inspect-${inspector.id}"
        stream.to(inspectTopic)
    }
}
