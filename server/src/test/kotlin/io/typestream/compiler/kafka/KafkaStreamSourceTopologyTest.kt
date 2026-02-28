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
    fun `DataStreamSerde serializes tombstone as null bytes and deserializes back to null`() {
        val builder = StreamsBuilder()
        val serde = DataStreamSerde()

        val stream = builder.stream("input", Consumed.with(serde, serde))

        // This tests the serde round-trip that underpins both the AVRO and PROTOBUF
        // branches in KafkaStreamSource.stream(). Both branches use v?.let { ... }
        // which relies on DataStreamSerde returning null for null bytes.
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

    @Test
    fun `tableMaterialize count reflects tombstone deletions`() {
        val builder = StreamsBuilder()
        val serde = DataStreamSerde()
        val longSerde = org.apache.kafka.common.serialization.Serdes.Long()

        val stream = builder.stream("input", Consumed.with(serde, serde))

        // Mimic tableMaterialize count: stream → toTable → groupBy(identity) → count
        val table = stream.toTable()
        table.groupBy { k, v -> org.apache.kafka.streams.KeyValue.pair(k, v) }
            .count(
                Materialized.`as`<DataStream, Long, KeyValueStore<Bytes, ByteArray>>("count-store")
                    .withKeySerde(serde)
                    .withValueSerde(longSerde)
            )

        val driver = TopologyTestDriver(builder.build(), testProperties())
        val input = driver.createInputTopic("input", serde.serializer(), serde.serializer())

        val key1 = testKey("key-1")
        val key2 = testKey("key-2")

        input.pipeInput(key1, testDataStream("Book A", 100))
        input.pipeInput(key2, testDataStream("Book B", 200))

        val store = driver.getKeyValueStore<DataStream, Long>("count-store")
        assertThat(store.get(key1)).isEqualTo(1L)
        assertThat(store.get(key2)).isEqualTo(1L)

        // Tombstone key-1 — count should drop to 0
        input.pipeInput(key1, null as DataStream?)

        assertThat(store.get(key1)).isEqualTo(0L)
        assertThat(store.get(key2)).isEqualTo(1L)

        driver.close()
    }

    @Test
    fun `tableMaterialize with groupByField handles tombstone`() {
        val builder = StreamsBuilder()
        val serde = DataStreamSerde()
        val longSerde = org.apache.kafka.common.serialization.Serdes.Long()

        val stream = builder.stream("input", Consumed.with(serde, serde))

        // Mimic tableMaterialize with groupByField count: toTable → groupBy(field) → count
        val table = stream.toTable()
        table.groupBy { _, v ->
            val groupKey = DataStream(v.path, v.schema.selectOne("title") ?: Schema.String(""))
            org.apache.kafka.streams.KeyValue.pair(groupKey, v)
        }.count(
            Materialized.`as`<DataStream, Long, KeyValueStore<Bytes, ByteArray>>("group-count-store")
                .withKeySerde(serde)
                .withValueSerde(longSerde)
        )

        val driver = TopologyTestDriver(builder.build(), testProperties())
        val input = driver.createInputTopic("input", serde.serializer(), serde.serializer())

        val groupKey = DataStream("/dev/kafka/local/topics/books", Schema.String("Dune"))

        // Two records with same title grouped together
        input.pipeInput(testKey("key-1"), testDataStream("Dune", 100))
        input.pipeInput(testKey("key-2"), testDataStream("Dune", 200))

        val store = driver.getKeyValueStore<DataStream, Long>("group-count-store")
        assertThat(store.get(groupKey)).isEqualTo(2L)

        // Tombstone key-1 — group count should drop to 1
        input.pipeInput(testKey("key-1"), null as DataStream?)

        assertThat(store.get(groupKey)).isEqualTo(1L)

        driver.close()
    }

    @Test
    fun `stream handles null key`() {
        val builder = StreamsBuilder()
        val serde = DataStreamSerde()

        val stream = builder.stream("input", Consumed.with(serde, serde))

        stream.to("output", Produced.with(serde, serde))

        val driver = TopologyTestDriver(builder.build(), testProperties())
        val input = driver.createInputTopic("input", serde.serializer(), serde.serializer())
        val output = driver.createOutputTopic("output", serde.deserializer(), serde.deserializer())

        val value = testDataStream("Keyless Book", 100)

        // Pipe record with null key
        input.pipeInput(null as DataStream?, value)

        val records = output.readRecordsToList()
        assertThat(records).hasSize(1)
        assertThat(records[0].key()).isNull()
        assertThat(records[0].value()).isEqualTo(value)

        driver.close()
    }

    @Test
    fun `CDC unwrap filters out tombstones before accessing schema`() {
        val builder = StreamsBuilder()
        val serde = DataStreamSerde()

        val stream = builder.stream("input", Consumed.with(serde, serde))

        // Replicate the CDC unwrap pipeline from KafkaStreamSource.applyCdcUnwrap():
        // 1. Filter: v != null && 'after' field is not null
        // 2. MapValues: extract 'after' fields to top level
        stream
            .filter { _, v ->
                if (v == null) return@filter false
                val schema = v.schema
                if (schema !is Schema.Struct) return@filter false
                val afterField = schema.value.find { it.name == "after" } ?: return@filter false
                afterField.value is Schema.Struct || (afterField.value is Schema.Optional && (afterField.value as Schema.Optional).value is Schema.Struct)
            }
            .mapValues { v ->
                val schema = v.schema as Schema.Struct
                val afterField = schema.value.find { it.name == "after" }!!
                val afterStruct = when (val av = afterField.value) {
                    is Schema.Struct -> av
                    is Schema.Optional -> av.value as Schema.Struct
                    else -> error("unexpected")
                }
                DataStream(v.path, afterStruct)
            }
            .to("output", Produced.with(serde, serde))

        val driver = TopologyTestDriver(builder.build(), testProperties())
        val input = driver.createInputTopic("input", serde.serializer(), serde.serializer())
        val output = driver.createOutputTopic("output", serde.deserializer(), serde.deserializer())

        val key = testKey("key-1")

        // CDC INSERT record with 'after' containing data
        val cdcInsert = DataStream(
            "/dev/kafka/local/topics/cdc-topic",
            Schema.Struct(
                listOf(
                    Schema.Field("before", Schema.Optional(null)),
                    Schema.Field(
                        "after", Schema.Struct(
                            listOf(
                                Schema.Field("id", Schema.Int(1)),
                                Schema.Field("name", Schema.String("Alice"))
                            )
                        )
                    ),
                    Schema.Field("op", Schema.String("c"))
                )
            )
        )

        input.pipeInput(key, cdcInsert)

        // Tombstone — should be filtered out without NPE
        input.pipeInput(key, null as DataStream?)

        val records = output.readRecordsToList()
        assertThat(records).hasSize(1)

        // Verify the unwrapped record has the 'after' fields at top level
        val unwrapped = records[0].value()
        assertThat(unwrapped.schema).isInstanceOf(Schema.Struct::class.java)
        val fields = (unwrapped.schema as Schema.Struct).value.map { it.name }
        assertThat(fields).containsExactly("id", "name")

        driver.close()
    }
}
