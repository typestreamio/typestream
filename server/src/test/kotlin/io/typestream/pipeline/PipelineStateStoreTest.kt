package io.typestream.pipeline

import io.typestream.config.KafkaConfig
import io.typestream.config.SchemaRegistryConfig
import io.typestream.grpc.job_service.Job
import io.typestream.grpc.pipeline_service.Pipeline
import io.typestream.testing.TestKafkaContainer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.testcontainers.junit.jupiter.Testcontainers

@Testcontainers
internal class PipelineStateStoreTest {

    companion object {
        private val testKafka = TestKafkaContainer.instance
    }

    private lateinit var stateStore: PipelineStateStore

    @BeforeEach
    fun beforeEach() {
        val kafkaConfig = KafkaConfig(
            bootstrapServers = testKafka.bootstrapServers,
            schemaRegistry = SchemaRegistryConfig(url = testKafka.schemaRegistryAddress)
        )
        stateStore = PipelineStateStore(kafkaConfig)
        stateStore.ensureTopicExists()
    }

    private fun buildMetadata(name: String, version: String = "1", description: String = ""): Pipeline.PipelineMetadata {
        return Pipeline.PipelineMetadata.newBuilder()
            .setName(name)
            .setVersion(version)
            .setDescription(description)
            .build()
    }

    private fun buildSimpleGraph(): Job.PipelineGraph {
        val sourceNode = Job.PipelineNode.newBuilder()
            .setId("source-1")
            .setStreamSource(
                Job.StreamSourceNode.newBuilder()
                    .setDataStream(Job.DataStreamProto.newBuilder().setPath("/dev/kafka/local/topics/test"))
                    .setEncoding(Job.Encoding.AVRO)
            )
            .build()

        val filterNode = Job.PipelineNode.newBuilder()
            .setId("filter-1")
            .setFilter(
                Job.FilterNode.newBuilder()
                    .setByKey(false)
                    .setPredicate(Job.PredicateProto.newBuilder().setExpr("test"))
            )
            .build()

        return Job.PipelineGraph.newBuilder()
            .addNodes(sourceNode)
            .addNodes(filterNode)
            .addEdges(Job.PipelineEdge.newBuilder().setFromId("source-1").setToId("filter-1"))
            .build()
    }

    @Test
    fun `save and load roundtrip`() {
        val name = "test-roundtrip-${System.nanoTime()}"
        val record = PipelineRecord(
            metadata = buildMetadata(name, "1", "Test pipeline"),
            graph = buildSimpleGraph(),
            appliedAt = System.currentTimeMillis()
        )

        stateStore.save(name, record)

        val loaded = stateStore.load()
        assertThat(loaded).containsKey(name)
        assertThat(loaded[name]!!.metadata.name).isEqualTo(name)
        assertThat(loaded[name]!!.metadata.version).isEqualTo("1")
        assertThat(loaded[name]!!.metadata.description).isEqualTo("Test pipeline")
        assertThat(loaded[name]!!.graph).isEqualTo(record.graph)
    }

    @Test
    fun `delete produces tombstone`() {
        val name = "test-delete-${System.nanoTime()}"
        val record = PipelineRecord(
            metadata = buildMetadata(name),
            graph = buildSimpleGraph(),
            appliedAt = System.currentTimeMillis()
        )

        stateStore.save(name, record)
        stateStore.delete(name)

        val loaded = stateStore.load()
        assertThat(loaded).doesNotContainKey(name)
    }

    @Test
    fun `multiple pipelines`() {
        val suffix = System.nanoTime()
        val name1 = "test-multi-1-$suffix"
        val name2 = "test-multi-2-$suffix"

        stateStore.save(name1, PipelineRecord(
            metadata = buildMetadata(name1, "1"),
            graph = buildSimpleGraph(),
            appliedAt = System.currentTimeMillis()
        ))
        stateStore.save(name2, PipelineRecord(
            metadata = buildMetadata(name2, "2"),
            graph = buildSimpleGraph(),
            appliedAt = System.currentTimeMillis()
        ))

        val loaded = stateStore.load()
        assertThat(loaded).containsKey(name1)
        assertThat(loaded).containsKey(name2)
        assertThat(loaded[name1]!!.metadata.version).isEqualTo("1")
        assertThat(loaded[name2]!!.metadata.version).isEqualTo("2")
    }

    @Test
    fun `overwrite returns latest`() {
        val name = "test-overwrite-${System.nanoTime()}"

        stateStore.save(name, PipelineRecord(
            metadata = buildMetadata(name, "1"),
            graph = buildSimpleGraph(),
            appliedAt = 1000L
        ))
        stateStore.save(name, PipelineRecord(
            metadata = buildMetadata(name, "2"),
            graph = buildSimpleGraph(),
            appliedAt = 2000L
        ))

        val loaded = stateStore.load()
        assertThat(loaded).containsKey(name)
        assertThat(loaded[name]!!.metadata.version).isEqualTo("2")
        assertThat(loaded[name]!!.appliedAt).isEqualTo(2000L)
    }
}
