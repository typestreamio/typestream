package io.typestream.compiler.kafka

import io.typestream.compiler.node.KeyValue
import io.typestream.compiler.node.Node
import io.typestream.compiler.types.DataStream
import io.typestream.compiler.types.Encoding
import io.typestream.compiler.types.datastream.fromAvroGenericRecord
import io.typestream.compiler.types.datastream.fromBytes
import io.typestream.compiler.types.datastream.fromProtoMessage
import io.typestream.compiler.types.datastream.join
import io.typestream.compiler.types.datastream.toAvroGenericRecord
import io.typestream.compiler.types.datastream.toAvroSchema
import io.typestream.compiler.types.datastream.toBytes
import io.typestream.compiler.types.datastream.toProtoMessage
import io.typestream.compiler.types.datastream.toProtoSchema
import io.typestream.kafka.avro.AvroSerde
import io.typestream.kafka.ProtoSerde
import io.typestream.kafka.StreamsBuilderWrapper
import org.apache.kafka.common.serialization.Serdes
import org.apache.kafka.streams.KeyValue.pair
import org.apache.kafka.streams.kstream.Consumed
import org.apache.kafka.streams.kstream.JoinWindows
import org.apache.kafka.streams.kstream.KGroupedStream
import org.apache.kafka.streams.kstream.KStream
import org.apache.kafka.streams.kstream.Produced
import java.time.Duration

// TODO we need to support schemas for keys
data class KafkaStreamSource(val node: Node.StreamSource, private val streamsBuilder: StreamsBuilderWrapper) {
    private var stream: KStream<DataStream, DataStream> = stream(node.dataStream)
    private var groupedStream: KGroupedStream<DataStream, DataStream>? = null

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
                        DataStream.fromBytes(dataStream.path, k), DataStream.fromAvroGenericRecord(dataStream.path, v)
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
                        DataStream.fromBytes(dataStream.path, k), DataStream.fromProtoMessage(dataStream.path, v)
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

    fun count() {
        requireNotNull(groupedStream) { "cannot count a non-grouped stream" }

        stream = groupedStream!!.count().mapValues { v -> DataStream.fromLong("", v) }.toStream()
    }
}
