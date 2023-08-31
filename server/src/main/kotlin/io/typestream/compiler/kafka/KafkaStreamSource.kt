package io.typestream.compiler.kafka

import io.typestream.compiler.node.KeyValue
import io.typestream.compiler.node.Node
import io.typestream.compiler.types.DataStream
import io.typestream.compiler.types.Encoding
import io.typestream.compiler.types.datastream.fromAvroGenericRecord
import io.typestream.compiler.types.datastream.fromBytes
import io.typestream.compiler.types.datastream.join
import io.typestream.compiler.types.datastream.toAvroGenericRecord
import io.typestream.compiler.types.datastream.toAvroSchema
import io.typestream.compiler.types.datastream.toBytes
import io.typestream.kafka.AvroSchemaSerde
import io.typestream.kafka.StreamsBuilderWrapper
import org.apache.kafka.common.serialization.Serdes
import org.apache.kafka.streams.KeyValue.pair
import org.apache.kafka.streams.kstream.Consumed
import org.apache.kafka.streams.kstream.JoinWindows
import org.apache.kafka.streams.kstream.KGroupedStream
import org.apache.kafka.streams.kstream.KStream
import org.apache.kafka.streams.kstream.Produced
import java.time.Duration

data class KafkaStreamSource(val node: Node.StreamSource, private val streamsBuilder: StreamsBuilderWrapper) {
    private var stream: KStream<DataStream, DataStream> = stream(node.dataStream)
    private var groupedStream: KGroupedStream<DataStream, DataStream>? = null

    // TODO we need a BytesOrAvroSerde for keys
    private fun stream(dataStream: DataStream): KStream<DataStream, DataStream> {
        return when (node.encoding) {
            Encoding.AVRO -> streamsBuilder.stream(
                dataStream.name, Consumed.with(Serdes.Bytes(), AvroSchemaSerde(dataStream.toAvroSchema()))
            ).map { k, v ->
                pair(
                    DataStream.fromBytes(dataStream.path, k), DataStream.fromAvroGenericRecord(dataStream.path, v)
                )
            }

            else -> streamsBuilder.stream(dataStream.name)
        }
    }

    // TODO we need a BytesOrAvroSerde for keys
    fun to(node: Node.Sink) {
        when (node.encoding) {
            Encoding.AVRO -> {
                val keySerde = Serdes.Bytes()
                keySerde.configure(streamsBuilder.config.toMutableMap(), true)

                val valueSerde = AvroSchemaSerde(node.output.toAvroSchema())
                valueSerde.configure(streamsBuilder.config.toMutableMap(), false)

                stream.map { k, v ->
                    pair(k.toBytes(), v.toAvroGenericRecord())
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

    fun join(join: Node.Join) {
        val windowSize = Duration.ofMinutes(5)
        val advance = Duration.ofMinutes(1)
        val window = JoinWindows.ofTimeDifferenceAndGrace(windowSize, advance)
        val with = stream(join.with)

        stream = stream.join(with, { left, right -> left.join(right) }, window)
    }

    fun filter(filter: Node.Filter) {
        stream = stream.filter { _, v -> filter.predicate.matches(v) }
    }

    fun group(group: Node.Group) {
        groupedStream = stream.groupBy { k, v -> group.keyMapper(KeyValue(k, v)) }
    }

    fun count() {
        requireNotNull(groupedStream) { "cannot count a non-grouped stream" }

        stream = groupedStream!!.count().mapValues { v -> DataStream.fromLong("", v) }.toStream()
    }
}
