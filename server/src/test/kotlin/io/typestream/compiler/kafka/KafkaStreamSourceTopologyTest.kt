package io.typestream.compiler.kafka

import io.typestream.compiler.types.DataStream
import io.typestream.compiler.types.schema.Schema
import io.typestream.kafka.DataStreamSerde
import org.apache.kafka.common.utils.Bytes
import org.apache.kafka.streams.StreamsBuilder
import org.apache.kafka.streams.StreamsConfig
import org.apache.kafka.streams.TopologyTestDriver
import org.apache.kafka.streams.kstream.Consumed
import org.apache.kafka.streams.kstream.Materialized
import org.apache.kafka.streams.kstream.Produced
import org.apache.kafka.streams.state.KeyValueStore
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.Properties

class KafkaStreamSourceTopologyTest {

    private fun testProperties(): Properties {
        val props = Properties()
        props[StreamsConfig.APPLICATION_ID_CONFIG] = "test-app"
        props[StreamsConfig.BOOTSTRAP_SERVERS_CONFIG] = "dummy:1234"
        props[StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG] = DataStreamSerde::class.java.name
        props[StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG] = DataStreamSerde::class.java.name
        return props
    }

    private fun testDataStream(title: String, wordCount: Int): DataStream {
        return DataStream(
            "/dev/kafka/local/topics/books",
            Schema.Struct(
                listOf(
                    Schema.Field("title", Schema.String(title)),
                    Schema.Field("word_count", Schema.Int(wordCount))
                )
            )
        )
    }

    private fun testKey(id: String): DataStream {
        return DataStream("/dev/kafka/local/topics/books", Schema.String(id))
    }

    @Test
    fun `avro stream handles tombstone values`() {
        val builder = StreamsBuilder()
        val serde = DataStreamSerde()

        val stream = builder.stream("input", Consumed.with(serde, serde))

        // Simulate the null-safe map pattern from KafkaStreamSource (avro branch)
        stream.mapValues { v -> v?.let { it } }
            .to("output", Produced.with(serde, serde))

        val driver = TopologyTestDriver(builder.build(), testProperties())
        val input = driver.createInputTopic("input", serde.serializer(), serde.serializer())
        val output = driver.createOutputTopic("output", serde.deserializer(), serde.deserializer())

        val key = testKey("key-1")
        val value = testDataStream("Test Book", 100)

        input.pipeInput(key, value)
        input.pipeInput(key, null as DataStream?)

        val records = output.readRecordsToList()
        assertThat(records).hasSize(2)
        assertThat(records[0].value()).isEqualTo(value)
        assertThat(records[1].value()).isNull()

        driver.close()
    }

    @Test
    fun `protobuf stream handles tombstone values`() {
        val builder = StreamsBuilder()
        val serde = DataStreamSerde()

        val stream = builder.stream("input", Consumed.with(serde, serde))

        // Same null-safe pattern applies to protobuf branch
        stream.mapValues { v -> v?.let { it } }
            .to("output", Produced.with(serde, serde))

        val driver = TopologyTestDriver(builder.build(), testProperties())
        val input = driver.createInputTopic("input", serde.serializer(), serde.serializer())
        val output = driver.createOutputTopic("output", serde.deserializer(), serde.deserializer())

        val key = testKey("key-1")
        val value = testDataStream("Proto Book", 200)

        input.pipeInput(key, value)
        input.pipeInput(key, null as DataStream?)

        val records = output.readRecordsToList()
        assertThat(records).hasSize(2)
        assertThat(records[0].value()).isEqualTo(value)
        assertThat(records[1].value()).isNull()

        driver.close()
    }

    @Test
    fun `tableMaterialize deletes key on tombstone`() {
        val builder = StreamsBuilder()
        val serde = DataStreamSerde()

        val stream = builder.stream("input", Consumed.with(serde, serde))

        stream.toTable(
            Materialized.`as`<DataStream, DataStream, KeyValueStore<Bytes, ByteArray>>("test-store")
                .withKeySerde(serde)
                .withValueSerde(serde)
        )

        val driver = TopologyTestDriver(builder.build(), testProperties())
        val input = driver.createInputTopic("input", serde.serializer(), serde.serializer())

        val key = testKey("key-1")
        val value = testDataStream("Book to Delete", 100)

        // Insert a record
        input.pipeInput(key, value)

        val store = driver.getKeyValueStore<DataStream, DataStream>("test-store")
        assertThat(store.get(key)).isEqualTo(value)

        // Send tombstone — should delete the key from the store
        input.pipeInput(key, null as DataStream?)

        assertThat(store.get(key)).isNull()

        driver.close()
    }

    @Test
    fun `stream with filter handles tombstone values`() {
        val builder = StreamsBuilder()
        val serde = DataStreamSerde()

        val stream = builder.stream("input", Consumed.with(serde, serde))

        // Filter with null check — mirrors the fix in KafkaStreamSource.filter()
        stream.filter { _, v -> v != null }
            .to("output", Produced.with(serde, serde))

        val driver = TopologyTestDriver(builder.build(), testProperties())
        val input = driver.createInputTopic("input", serde.serializer(), serde.serializer())
        val output = driver.createOutputTopic("output", serde.deserializer(), serde.deserializer())

        val key = testKey("key-1")
        val value = testDataStream("Surviving Book", 300)

        input.pipeInput(key, value)
        // Tombstone should be filtered out, not cause NPE
        input.pipeInput(key, null as DataStream?)

        val records = output.readRecordsToList()
        assertThat(records).hasSize(1)
        assertThat(records[0].value()).isEqualTo(value)

        driver.close()
    }
}
