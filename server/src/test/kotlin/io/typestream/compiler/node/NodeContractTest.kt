package io.typestream.compiler.node

import io.typestream.compiler.ast.Predicate
import io.typestream.compiler.types.DataStream
import io.typestream.compiler.types.Encoding
import io.typestream.compiler.types.InferenceContext
import io.typestream.compiler.types.schema.Schema
import io.typestream.grpc.job_service.Job
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.util.stream.Stream

internal class NodeContractTest {

    companion object {
        private val sampleStructSchema = Schema.Struct(
            listOf(
                Schema.Field("id", Schema.String("123")),
                Schema.Field("name", Schema.String("test")),
                Schema.Field("ip_address", Schema.String("8.8.8.8")),
                Schema.Field("file_path", Schema.String("/path/to/file.pdf")),
                Schema.Field("text_content", Schema.String("Hello world"))
            )
        )

        private val sampleDataStream = DataStream("/test/topic", sampleStructSchema)

        private val mockContext = object : InferenceContext {
            override fun lookupDataStream(path: String) = DataStream(path, sampleStructSchema)
            override fun lookupEncoding(path: String) = Encoding.AVRO
        }

        private val mockExecutionContext = ExecutionContext()

        private val mockFromProtoContext = FromProtoContext(
            inferredSchemas = mapOf(
                "sink-1" to DataStream("/dev/kafka/local/topics/output", sampleStructSchema),
            ),
            inferredEncodings = mapOf(
                "source-1" to Encoding.AVRO,
                "sink-1" to Encoding.AVRO,
            ),
            findDataStream = { path -> DataStream(path, sampleStructSchema) }
        )

        @JvmStatic
        fun allNodes(): Stream<Arguments> = Stream.of(
            Arguments.of("Count", Node.Count("count-1")),
            Arguments.of("WindowedCount", Node.WindowedCount("wc-1", 60)),
            Arguments.of("Filter", Node.Filter("filter-1", false, Predicate.matches(".*"))),
            Arguments.of("Group", Node.Group("group-1", ".name") { kv -> kv.value.select(listOf("name")) }),
            Arguments.of("Join", Node.Join("join-1", sampleDataStream, JoinType(byKey = true, isLookup = false))),
            Arguments.of("Map", Node.Map("map-1", "select .id .name") { kv -> KeyValue(kv.key, kv.value.select(listOf("id", "name"))) }),
            Arguments.of("NoOp", Node.NoOp("noop-1")),
            Arguments.of("ShellSource", Node.ShellSource("shell-1", listOf(sampleDataStream))),
            Arguments.of("StreamSource", Node.StreamSource("source-1", sampleDataStream, Encoding.AVRO, false)),
            Arguments.of("Each", Node.Each("each-1") { _ -> }),
            Arguments.of("Sink", Node.Sink("sink-1", sampleDataStream, Encoding.AVRO)),
            Arguments.of("GeoIp", Node.GeoIp("geoip-1", "ip_address", "country_code")),
            Arguments.of("Inspector", Node.Inspector("inspector-1", "test-label")),
            Arguments.of("ReduceLatest", Node.ReduceLatest("reduce-1")),
            Arguments.of("TextExtractor", Node.TextExtractor("text-1", "file_path", "extracted_text")),
            Arguments.of("EmbeddingGenerator", Node.EmbeddingGenerator("embed-1", "text_content", "embedding", "text-embedding-3-small")),
            Arguments.of("OpenAiTransformer", Node.OpenAiTransformer("ai-1", "Summarize this", "ai_response", "gpt-4o-mini")),
        )

        @JvmStatic
        fun transformNodes(): Stream<Arguments> = Stream.of(
            Arguments.of("Count", Node.Count("count-1")),
            Arguments.of("WindowedCount", Node.WindowedCount("wc-1", 60)),
            Arguments.of("Filter", Node.Filter("filter-1", false, Predicate.matches(".*"))),
            Arguments.of("Group", Node.Group("group-1", ".name") { kv -> kv.value.select(listOf("name")) }),
            Arguments.of("Join", Node.Join("join-1", sampleDataStream, JoinType(byKey = true, isLookup = false))),
            Arguments.of("Map", Node.Map("map-1", "select .id .name") { kv -> KeyValue(kv.key, kv.value.select(listOf("id", "name"))) }),
            Arguments.of("Each", Node.Each("each-1") { _ -> }),
            Arguments.of("GeoIp", Node.GeoIp("geoip-1", "ip_address", "country_code")),
            Arguments.of("Inspector", Node.Inspector("inspector-1", "test-label")),
            Arguments.of("ReduceLatest", Node.ReduceLatest("reduce-1")),
            Arguments.of("TextExtractor", Node.TextExtractor("text-1", "file_path", "extracted_text")),
            Arguments.of("EmbeddingGenerator", Node.EmbeddingGenerator("embed-1", "text_content", "embedding", "text-embedding-3-small")),
            Arguments.of("OpenAiTransformer", Node.OpenAiTransformer("ai-1", "Summarize this", "ai_response", "gpt-4o-mini")),
            Arguments.of("Sink", Node.Sink("sink-1", sampleDataStream, Encoding.AVRO)),
        )

        @JvmStatic
        fun sourceNodes(): Stream<Arguments> = Stream.of(
            Arguments.of("ShellSource", Node.ShellSource("shell-1", listOf(sampleDataStream))),
            Arguments.of("StreamSource", Node.StreamSource("source-1", sampleDataStream, Encoding.AVRO, false)),
        )

        @JvmStatic
        fun shellExecutableNodes(): Stream<Arguments> = Stream.of(
            Arguments.of("Filter", Node.Filter("filter-1", false, Predicate.matches(".*"))),
            Arguments.of("Map", Node.Map("map-1") { kv -> kv }),
            Arguments.of("Each", Node.Each("each-1") { _ -> }),
            Arguments.of("NoOp", Node.NoOp("noop-1")),
            Arguments.of("ShellSource", Node.ShellSource("shell-1", listOf(sampleDataStream))),
            Arguments.of("Inspector", Node.Inspector("inspector-1", "test-label")),
        )
    }

    // --- Contract: every node has an id ---

    @ParameterizedTest(name = "{0}")
    @MethodSource("allNodes")
    fun `every node has an id`(name: String, node: Node) {
        assertThat(node.id).isNotBlank()
    }

    // --- Contract: schema inference ---

    @ParameterizedTest(name = "{0}")
    @MethodSource("transformNodes")
    fun `transform and sink nodes can infer output schema with input`(name: String, node: Node) {
        assertDoesNotThrow {
            node.inferOutputSchema(sampleDataStream, Encoding.AVRO, mockContext)
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("sourceNodes")
    fun `source nodes can infer output schema without input`(name: String, node: Node) {
        assertDoesNotThrow {
            node.inferOutputSchema(null, null, mockContext)
        }
    }

    // --- Contract: toProto ---

    @ParameterizedTest(name = "{0}")
    @MethodSource("allNodes")
    fun `every node can serialize to proto`(name: String, node: Node) {
        val proto = node.toProto()
        assertThat(proto.id).isEqualTo(node.id)
        assertThat(proto.nodeTypeCase).isNotEqualTo(Job.PipelineNode.NodeTypeCase.NODETYPE_NOT_SET)
    }

    // --- Contract: proto round-trip ---

    @ParameterizedTest(name = "{0}")
    @MethodSource("allNodes")
    fun `every node round-trips through proto`(name: String, node: Node) {
        val proto = node.toProto()
        val roundTripped = Node.fromProto(proto, mockFromProtoContext)

        assertThat(roundTripped.id).isEqualTo(node.id)
        assertThat(roundTripped::class).isEqualTo(node::class)
    }

    // --- Contract: shell execution ---

    @ParameterizedTest(name = "{0}")
    @MethodSource("shellExecutableNodes")
    fun `shell-executable nodes can execute in shell mode`(name: String, node: Node) {
        assertDoesNotThrow {
            node.applyToShell(listOf(sampleDataStream), mockExecutionContext)
        }
    }

    // --- Specific round-trip tests ---

    @Test
    fun `Filter round-trips predicate expression`() {
        val original = Node.Filter("f1", true, Predicate.matches("hello"))
        val proto = original.toProto()
        val roundTripped = Node.fromProto(proto, mockFromProtoContext) as Node.Filter

        assertThat(roundTripped.byKey).isEqualTo(original.byKey)
        // Predicate round-trips through expression string
        assertThat(roundTripped.predicate.toExpr()).isEqualTo(original.predicate.toExpr())
    }

    @Test
    fun `StreamSource round-trips encoding and unwrapCdc`() {
        val original = Node.StreamSource("s1", sampleDataStream, Encoding.JSON, true)
        val proto = original.toProto()

        assertThat(proto.streamSource.unwrapCdc).isTrue()
        assertThat(proto.streamSource.encoding).isEqualTo(Job.Encoding.JSON)
    }

    @Test
    fun `WindowedCount round-trips window size`() {
        val original = Node.WindowedCount("wc1", 300)
        val proto = original.toProto()
        val roundTripped = Node.fromProto(proto, mockFromProtoContext) as Node.WindowedCount

        assertThat(roundTripped.windowSizeSeconds).isEqualTo(300)
    }

    @Test
    fun `GeoIp round-trips field configuration`() {
        val original = Node.GeoIp("g1", "user_ip", "country")
        val proto = original.toProto()
        val roundTripped = Node.fromProto(proto, mockFromProtoContext) as Node.GeoIp

        assertThat(roundTripped.ipField).isEqualTo("user_ip")
        assertThat(roundTripped.outputField).isEqualTo("country")
    }

    @Test
    fun `EmbeddingGenerator round-trips model and fields`() {
        val original = Node.EmbeddingGenerator("e1", "description", "vector", "text-embedding-3-small")
        val proto = original.toProto()
        val roundTripped = Node.fromProto(proto, mockFromProtoContext) as Node.EmbeddingGenerator

        assertThat(roundTripped.textField).isEqualTo("description")
        assertThat(roundTripped.outputField).isEqualTo("vector")
        assertThat(roundTripped.model).isEqualTo("text-embedding-3-small")
    }

    @Test
    fun `OpenAiTransformer round-trips prompt and model`() {
        val original = Node.OpenAiTransformer("o1", "Summarize", "summary", "gpt-4o-mini")
        val proto = original.toProto()
        val roundTripped = Node.fromProto(proto, mockFromProtoContext) as Node.OpenAiTransformer

        assertThat(roundTripped.prompt).isEqualTo("Summarize")
        assertThat(roundTripped.outputField).isEqualTo("summary")
        assertThat(roundTripped.model).isEqualTo("gpt-4o-mini")
    }

    @Test
    fun `Group round-trips keyMapperExpr`() {
        val original = Node.Group("g1", ".user_id") { kv -> kv.value.select(listOf("user_id")) }
        val proto = original.toProto()
        val roundTripped = Node.fromProto(proto, mockFromProtoContext) as Node.Group

        assertThat(roundTripped.keyMapperExpr).isEqualTo(".user_id")
    }

    @Test
    fun `Map round-trips mapperExpr`() {
        val original = Node.Map("m1", "select .id .name") { kv -> KeyValue(kv.key, kv.value.select(listOf("id", "name"))) }
        val proto = original.toProto()
        val roundTripped = Node.fromProto(proto, mockFromProtoContext) as Node.Map

        assertThat(roundTripped.mapperExpr).isEqualTo("select .id .name")
    }

    @Test
    fun `EmbeddingGenerator fromProto uses defaults for blank fields`() {
        val proto = Job.PipelineNode.newBuilder()
            .setId("e1")
            .setEmbeddingGenerator(
                Job.EmbeddingGeneratorNode.newBuilder()
                    .setTextField("text")
                    // leave outputField and model blank
            )
            .build()

        val node = Node.fromProto(proto, mockFromProtoContext) as Node.EmbeddingGenerator
        assertThat(node.outputField).isEqualTo("embedding")
        assertThat(node.model).isEqualTo("text-embedding-3-small")
    }

    @Test
    fun `OpenAiTransformer fromProto uses defaults for blank fields`() {
        val proto = Job.PipelineNode.newBuilder()
            .setId("o1")
            .setOpenAiTransformer(
                Job.OpenAiTransformerNode.newBuilder()
                    .setPrompt("Do something")
                    // leave outputField and model blank
            )
            .build()

        val node = Node.fromProto(proto, mockFromProtoContext) as Node.OpenAiTransformer
        assertThat(node.outputField).isEqualTo("ai_response")
        assertThat(node.model).isEqualTo("gpt-4o-mini")
    }

    @Test
    fun `TextExtractor fromProto uses default for blank outputField`() {
        val proto = Job.PipelineNode.newBuilder()
            .setId("t1")
            .setTextExtractor(
                Job.TextExtractorNode.newBuilder()
                    .setFilePathField("path")
                    // leave outputField blank
            )
            .build()

        val node = Node.fromProto(proto, mockFromProtoContext) as Node.TextExtractor
        assertThat(node.outputField).isEqualTo("text")
    }

    // --- Completeness check ---

    @Test
    fun `all sealed subclasses have fixtures`() {
        val fixtureNames = allNodes().map { it.get()[0] as String }.toList().toSet()
        val sealedNames = Node::class.sealedSubclasses.map { it.simpleName }.toSet()
        assertThat(fixtureNames).isEqualTo(sealedNames)
    }
}
