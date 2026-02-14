package io.typestream.compiler.node

import io.typestream.compiler.ast.Predicate
import io.typestream.compiler.ast.PredicateParser
import io.typestream.compiler.toProtoEncoding
import io.typestream.compiler.types.DataStream
import io.typestream.compiler.types.Encoding
import io.typestream.compiler.types.InferenceContext
import io.typestream.compiler.types.InferenceResult
import io.typestream.compiler.types.schema.Schema
import io.typestream.embedding.EmbeddingGeneratorExecution
import io.typestream.geoip.GeoIpExecution
import io.typestream.grpc.job_service.Job
import io.typestream.openai.OpenAiTransformerExecution
import io.typestream.textextractor.TextExtractorExecution
import kotlinx.serialization.Serializable

@Serializable
sealed interface Node {
    val id: String

    fun inferOutputSchema(
        input: DataStream?,
        inputEncoding: Encoding?,
        context: InferenceContext
    ): InferenceResult

    fun toProto(): Job.PipelineNode

    fun applyToShell(dataStreams: List<DataStream>, context: ExecutionContext): List<DataStream>

    companion object {
        fun fromProto(proto: Job.PipelineNode, context: FromProtoContext): Node = when {
            proto.hasCount() -> Count.fromProto(proto, context)
            proto.hasWindowedCount() -> WindowedCount.fromProto(proto, context)
            proto.hasFilter() -> Filter.fromProto(proto, context)
            proto.hasGroup() -> Group.fromProto(proto, context)
            proto.hasJoin() -> Join.fromProto(proto, context)
            proto.hasMap() -> Map.fromProto(proto, context)
            proto.hasNoop() -> NoOp.fromProto(proto, context)
            proto.hasShellSource() -> ShellSource.fromProto(proto, context)
            proto.hasStreamSource() -> StreamSource.fromProto(proto, context)
            proto.hasEach() -> Each.fromProto(proto, context)
            proto.hasSink() -> Sink.fromProto(proto, context)
            proto.hasGeoIp() -> GeoIp.fromProto(proto, context)
            proto.hasInspector() -> Inspector.fromProto(proto, context)
            proto.hasReduceLatest() -> ReduceLatest.fromProto(proto, context)
            proto.hasTextExtractor() -> TextExtractor.fromProto(proto, context)
            proto.hasEmbeddingGenerator() -> EmbeddingGenerator.fromProto(proto, context)
            proto.hasOpenAiTransformer() -> OpenAiTransformer.fromProto(proto, context)
            else -> error("Unknown node type: $proto")
        }
    }

    @Serializable
    data class Count(override val id: String) : Node {
        override fun inferOutputSchema(
            input: DataStream?,
            inputEncoding: Encoding?,
            context: InferenceContext
        ): InferenceResult {
            val stream = input ?: error("count $id missing input")
            return InferenceResult(stream, inputEncoding ?: Encoding.AVRO)
        }

        override fun toProto(): Job.PipelineNode = Job.PipelineNode.newBuilder()
            .setId(id)
            .setCount(Job.CountNode.newBuilder())
            .build()

        override fun applyToShell(dataStreams: List<DataStream>, context: ExecutionContext): List<DataStream> =
            throw UnsupportedOperationException("Count not supported in shell mode")

        companion object {
            fun fromProto(proto: Job.PipelineNode, context: FromProtoContext): Count = Count(proto.id)
        }
    }

    @Serializable
    data class WindowedCount(override val id: String, val windowSizeSeconds: Long) : Node {
        override fun inferOutputSchema(
            input: DataStream?,
            inputEncoding: Encoding?,
            context: InferenceContext
        ): InferenceResult {
            val stream = input ?: error("windowedCount $id missing input")
            return InferenceResult(stream, inputEncoding ?: Encoding.AVRO)
        }

        override fun toProto(): Job.PipelineNode = Job.PipelineNode.newBuilder()
            .setId(id)
            .setWindowedCount(Job.WindowedCountNode.newBuilder().setWindowSizeSeconds(windowSizeSeconds))
            .build()

        override fun applyToShell(dataStreams: List<DataStream>, context: ExecutionContext): List<DataStream> =
            throw UnsupportedOperationException("WindowedCount not supported in shell mode")

        companion object {
            fun fromProto(proto: Job.PipelineNode, context: FromProtoContext): WindowedCount =
                WindowedCount(proto.id, proto.windowedCount.windowSizeSeconds)
        }
    }

    @Serializable
    data class Filter(override val id: String, val byKey: Boolean, val predicate: Predicate) : Node {
        override fun inferOutputSchema(
            input: DataStream?,
            inputEncoding: Encoding?,
            context: InferenceContext
        ): InferenceResult {
            val stream = input ?: error("filter $id missing input")
            return InferenceResult(stream, inputEncoding ?: Encoding.AVRO)
        }

        override fun toProto(): Job.PipelineNode = Job.PipelineNode.newBuilder()
            .setId(id)
            .setFilter(
                Job.FilterNode.newBuilder()
                    .setByKey(byKey)
                    .setPredicate(Job.PredicateProto.newBuilder().setExpr(predicate.toExpr()))
            )
            .build()

        override fun applyToShell(dataStreams: List<DataStream>, context: ExecutionContext): List<DataStream> =
            dataStreams.filter { predicate.matches(it) }

        companion object {
            fun fromProto(proto: Job.PipelineNode, context: FromProtoContext): Filter {
                val f = proto.filter
                return Filter(proto.id, f.byKey, PredicateParser.parse(f.predicate.expr))
            }
        }
    }

    @Serializable
    data class Group(
        override val id: String,
        val keyMapperExpr: String = "",
        val keyMapper: (KeyValue) -> DataStream
    ) : Node {
        override fun inferOutputSchema(
            input: DataStream?,
            inputEncoding: Encoding?,
            context: InferenceContext
        ): InferenceResult {
            val stream = input ?: error("group $id missing input")
            return InferenceResult(stream, inputEncoding ?: Encoding.AVRO)
        }

        override fun toProto(): Job.PipelineNode = Job.PipelineNode.newBuilder()
            .setId(id)
            .setGroup(Job.GroupNode.newBuilder().setKeyMapperExpr(keyMapperExpr))
            .build()

        override fun applyToShell(dataStreams: List<DataStream>, context: ExecutionContext): List<DataStream> =
            throw UnsupportedOperationException("Group not supported in shell mode")

        companion object {
            fun fromProto(proto: Job.PipelineNode, context: FromProtoContext): Group {
                val fieldPath = proto.group.keyMapperExpr
                val fields = fieldPath.trimStart('.').split('.').filter { it.isNotBlank() }
                return if (fields.isEmpty()) {
                    Group(proto.id, fieldPath) { kv -> kv.key }
                } else {
                    Group(proto.id, fieldPath) { kv -> kv.value.select(fields) }
                }
            }
        }
    }

    @Serializable
    data class Join(override val id: String, val with: DataStream, val joinType: JoinType) : Node {
        override fun inferOutputSchema(
            input: DataStream?,
            inputEncoding: Encoding?,
            context: InferenceContext
        ): InferenceResult {
            val stream = input ?: error("join $id missing input")
            val merged = stream.merge(with).copy(originalAvroSchema = null)
            return InferenceResult(merged, inputEncoding ?: Encoding.AVRO)
        }

        override fun toProto(): Job.PipelineNode = Job.PipelineNode.newBuilder()
            .setId(id)
            .setJoin(
                Job.JoinNode.newBuilder()
                    .setWith(Job.DataStreamProto.newBuilder().setPath(with.path))
                    .setJoinType(
                        Job.JoinTypeProto.newBuilder()
                            .setByKey(joinType.byKey)
                            .setIsLookup(joinType.isLookup)
                    )
            )
            .build()

        override fun applyToShell(dataStreams: List<DataStream>, context: ExecutionContext): List<DataStream> =
            throw UnsupportedOperationException("Join not supported in shell mode")

        companion object {
            fun fromProto(proto: Job.PipelineNode, context: FromProtoContext): Join {
                val j = proto.join
                val with = context.findDataStream(j.with.path)
                return Join(proto.id, with, JoinType(j.joinType.byKey, j.joinType.isLookup))
            }
        }
    }

    @Serializable
    data class Map(
        override val id: String,
        val mapperExpr: String = "",
        val mapper: (KeyValue) -> KeyValue
    ) : Node {
        override fun inferOutputSchema(
            input: DataStream?,
            inputEncoding: Encoding?,
            context: InferenceContext
        ): InferenceResult {
            // TODO: Extract field transformations from mapper lambda for accurate typing
            // For MVP, assume Map doesn't change schema (identity function)
            val stream = input ?: error("map $id missing input")
            return InferenceResult(stream, inputEncoding ?: Encoding.AVRO)
        }

        override fun toProto(): Job.PipelineNode = Job.PipelineNode.newBuilder()
            .setId(id)
            .setMap(Job.MapNode.newBuilder().setMapperExpr(mapperExpr))
            .build()

        override fun applyToShell(dataStreams: List<DataStream>, context: ExecutionContext): List<DataStream> =
            dataStreams.map { mapper(KeyValue(it, it)).value }

        companion object {
            fun fromProto(proto: Job.PipelineNode, context: FromProtoContext): Map {
                val expr = proto.map.mapperExpr
                return if (expr.startsWith("select ")) {
                    val fields = expr.removePrefix("select ").trim().split(" ").map { it.trimStart('.') }
                    Map(proto.id, expr) { kv -> KeyValue(kv.key, kv.value.select(fields)) }
                } else {
                    Map(proto.id, expr) { kv -> kv }
                }
            }
        }
    }

    @Serializable
    data class NoOp(override val id: String) : Node {
        override fun inferOutputSchema(
            input: DataStream?,
            inputEncoding: Encoding?,
            context: InferenceContext
        ): InferenceResult {
            val stream = input ?: DataStream("", Schema.String.zeroValue)
            return InferenceResult(stream, inputEncoding ?: Encoding.AVRO)
        }

        override fun toProto(): Job.PipelineNode = Job.PipelineNode.newBuilder()
            .setId(id)
            .setNoop(Job.NoOpNode.newBuilder())
            .build()

        override fun applyToShell(dataStreams: List<DataStream>, context: ExecutionContext): List<DataStream> =
            dataStreams

        companion object {
            fun fromProto(proto: Job.PipelineNode, context: FromProtoContext): NoOp = NoOp(proto.id)
        }
    }

    @Serializable
    data class ShellSource(override val id: String, val data: List<DataStream>) : Node {
        override fun inferOutputSchema(
            input: DataStream?,
            inputEncoding: Encoding?,
            context: InferenceContext
        ): InferenceResult {
            val stream = data.firstOrNull()
                ?: error("ShellSource $id has no data streams")
            return InferenceResult(stream, Encoding.JSON)
        }

        override fun toProto(): Job.PipelineNode = Job.PipelineNode.newBuilder()
            .setId(id)
            .setShellSource(
                Job.ShellSourceNode.newBuilder()
                    .addAllData(data.map { Job.DataStreamProto.newBuilder().setPath(it.path).build() })
            )
            .build()

        override fun applyToShell(dataStreams: List<DataStream>, context: ExecutionContext): List<DataStream> =
            dataStreams

        companion object {
            fun fromProto(proto: Job.PipelineNode, context: FromProtoContext): ShellSource {
                val data = proto.shellSource.dataList.map { dsProto -> context.findDataStream(dsProto.path) }
                return ShellSource(proto.id, data)
            }
        }
    }

    @Serializable
    data class StreamSource(
        override val id: String,
        val dataStream: DataStream,
        val encoding: Encoding,
        val unwrapCdc: Boolean = false
    ) : Node {
        override fun inferOutputSchema(
            input: DataStream?,
            inputEncoding: Encoding?,
            context: InferenceContext
        ): InferenceResult {
            val outputStream = if (unwrapCdc) dataStream.unwrapCdc() else dataStream
            return InferenceResult(outputStream, encoding)
        }

        override fun toProto(): Job.PipelineNode = Job.PipelineNode.newBuilder()
            .setId(id)
            .setStreamSource(
                Job.StreamSourceNode.newBuilder()
                    .setDataStream(Job.DataStreamProto.newBuilder().setPath(dataStream.path))
                    .setEncoding(encoding.toProtoEncoding())
                    .setUnwrapCdc(unwrapCdc)
            )
            .build()

        override fun applyToShell(dataStreams: List<DataStream>, context: ExecutionContext): List<DataStream> =
            throw UnsupportedOperationException("StreamSource not supported in shell mode")

        companion object {
            fun fromProto(proto: Job.PipelineNode, context: FromProtoContext): StreamSource {
                val ss = proto.streamSource
                val ds = context.findDataStream(ss.dataStream.path)
                val encoding = context.inferredEncodings[proto.id]
                    ?: error("No inferred encoding for stream source ${proto.id}")
                return StreamSource(proto.id, ds, encoding, ss.unwrapCdc)
            }
        }
    }

    @Serializable
    data class Each(
        override val id: String,
        val fnExpr: String = "",
        val fn: (KeyValue) -> Unit
    ) : Node {
        override fun inferOutputSchema(
            input: DataStream?,
            inputEncoding: Encoding?,
            context: InferenceContext
        ): InferenceResult {
            val stream = input ?: error("each $id missing input")
            return InferenceResult(stream, inputEncoding ?: Encoding.AVRO)
        }

        override fun toProto(): Job.PipelineNode = Job.PipelineNode.newBuilder()
            .setId(id)
            .setEach(Job.EachNode.newBuilder().setFnExpr(fnExpr))
            .build()

        override fun applyToShell(dataStreams: List<DataStream>, context: ExecutionContext): List<DataStream> {
            dataStreams.forEach { fn(KeyValue(it, it)) }
            return dataStreams
        }

        companion object {
            fun fromProto(proto: Job.PipelineNode, context: FromProtoContext): Each =
                Each(proto.id, proto.each.fnExpr) { _ -> }
        }
    }

    @Serializable
    data class Sink(override val id: String, val output: DataStream, val encoding: Encoding) : Node {
        override fun inferOutputSchema(
            input: DataStream?,
            inputEncoding: Encoding?,
            context: InferenceContext
        ): InferenceResult {
            val stream = input ?: error("sink $id missing input")
            val outputStream = stream.copy(path = output.path)
            return InferenceResult(outputStream, inputEncoding ?: Encoding.AVRO)
        }

        override fun toProto(): Job.PipelineNode = Job.PipelineNode.newBuilder()
            .setId(id)
            .setSink(
                Job.SinkNode.newBuilder()
                    .setOutput(Job.DataStreamProto.newBuilder().setPath(output.path))
                    .setEncoding(encoding.toProtoEncoding())
            )
            .build()

        override fun applyToShell(dataStreams: List<DataStream>, context: ExecutionContext): List<DataStream> =
            throw UnsupportedOperationException("Sink not supported in shell mode")

        companion object {
            fun fromProto(proto: Job.PipelineNode, context: FromProtoContext): Sink {
                val out = context.inferredSchemas[proto.id]
                    ?: error("No inferred schema for sink ${proto.id}")
                val encoding = context.inferredEncodings[proto.id]
                    ?: error("No inferred encoding for sink ${proto.id}")
                return Sink(proto.id, out, encoding)
            }
        }
    }

    @Serializable
    data class GeoIp(override val id: String, val ipField: String, val outputField: String) : Node {
        override fun inferOutputSchema(
            input: DataStream?,
            inputEncoding: Encoding?,
            context: InferenceContext
        ): InferenceResult {
            val stream = input ?: error("geoIp $id missing input")
            val inputSchema = stream.schema

            require(inputSchema is Schema.Struct) {
                "GeoIp requires struct schema, got: ${inputSchema::class.simpleName}"
            }

            val hasIpField = inputSchema.value.any { it.name == ipField }
            require(hasIpField) {
                "GeoIp IP field '$ipField' not found in schema. Available fields: ${inputSchema.value.map { it.name }}"
            }

            val newField = Schema.Field(outputField, Schema.String.zeroValue)
            val newFields = inputSchema.value + newField
            val outputStream = stream.copy(schema = Schema.Struct(newFields), originalAvroSchema = null)
            return InferenceResult(outputStream, inputEncoding ?: Encoding.AVRO)
        }

        override fun toProto(): Job.PipelineNode = Job.PipelineNode.newBuilder()
            .setId(id)
            .setGeoIp(
                Job.GeoIpNode.newBuilder()
                    .setIpField(ipField)
                    .setOutputField(outputField)
            )
            .build()

        override fun applyToShell(dataStreams: List<DataStream>, context: ExecutionContext): List<DataStream> {
            val service = context.geoIpService ?: error("GeoIpService not available")
            return GeoIpExecution.applyToShell(this, dataStreams, service)
        }

        companion object {
            fun fromProto(proto: Job.PipelineNode, context: FromProtoContext): GeoIp {
                val g = proto.geoIp
                return GeoIp(proto.id, g.ipField, g.outputField)
            }
        }
    }

    @Serializable
    data class Inspector(override val id: String, val label: String = "") : Node {
        override fun inferOutputSchema(
            input: DataStream?,
            inputEncoding: Encoding?,
            context: InferenceContext
        ): InferenceResult {
            val stream = input ?: error("inspector $id missing input")
            return InferenceResult(stream, inputEncoding ?: Encoding.AVRO)
        }

        override fun toProto(): Job.PipelineNode = Job.PipelineNode.newBuilder()
            .setId(id)
            .setInspector(Job.InspectorNode.newBuilder().setLabel(label))
            .build()

        override fun applyToShell(dataStreams: List<DataStream>, context: ExecutionContext): List<DataStream> =
            dataStreams

        companion object {
            fun fromProto(proto: Job.PipelineNode, context: FromProtoContext): Inspector =
                Inspector(proto.id, proto.inspector.label)
        }
    }

    @Serializable
    data class ReduceLatest(override val id: String) : Node {
        override fun inferOutputSchema(
            input: DataStream?,
            inputEncoding: Encoding?,
            context: InferenceContext
        ): InferenceResult {
            val stream = input ?: error("reduceLatest $id missing input")
            return InferenceResult(stream, inputEncoding ?: Encoding.AVRO)
        }

        override fun toProto(): Job.PipelineNode = Job.PipelineNode.newBuilder()
            .setId(id)
            .setReduceLatest(Job.ReduceLatestNode.newBuilder())
            .build()

        override fun applyToShell(dataStreams: List<DataStream>, context: ExecutionContext): List<DataStream> =
            throw UnsupportedOperationException("ReduceLatest not supported in shell mode")

        companion object {
            fun fromProto(proto: Job.PipelineNode, context: FromProtoContext): ReduceLatest =
                ReduceLatest(proto.id)
        }
    }

    @Serializable
    data class TextExtractor(override val id: String, val filePathField: String, val outputField: String) : Node {
        override fun inferOutputSchema(
            input: DataStream?,
            inputEncoding: Encoding?,
            context: InferenceContext
        ): InferenceResult {
            val stream = input ?: error("textExtractor $id missing input")
            val inputSchema = stream.schema

            require(inputSchema is Schema.Struct) {
                "TextExtractor requires struct schema, got: ${inputSchema::class.simpleName}"
            }

            val hasFilePathField = inputSchema.value.any { it.name == filePathField }
            require(hasFilePathField) {
                "TextExtractor file path field '$filePathField' not found in schema. Available fields: ${inputSchema.value.map { it.name }}"
            }

            val newField = Schema.Field(outputField, Schema.String.zeroValue)
            val newFields = inputSchema.value + newField
            val outputStream = stream.copy(schema = Schema.Struct(newFields), originalAvroSchema = null)
            return InferenceResult(outputStream, inputEncoding ?: Encoding.AVRO)
        }

        override fun toProto(): Job.PipelineNode = Job.PipelineNode.newBuilder()
            .setId(id)
            .setTextExtractor(
                Job.TextExtractorNode.newBuilder()
                    .setFilePathField(filePathField)
                    .setOutputField(outputField)
            )
            .build()

        override fun applyToShell(dataStreams: List<DataStream>, context: ExecutionContext): List<DataStream> {
            val service = context.textExtractorService ?: error("TextExtractorService not available")
            return TextExtractorExecution.applyToShell(this, dataStreams, service)
        }

        companion object {
            private const val DEFAULT_OUTPUT_FIELD = "text"

            fun fromProto(proto: Job.PipelineNode, context: FromProtoContext): TextExtractor {
                val t = proto.textExtractor
                val outputField = if (t.outputField.isBlank()) DEFAULT_OUTPUT_FIELD else t.outputField
                return TextExtractor(proto.id, t.filePathField, outputField)
            }
        }
    }

    @Serializable
    data class EmbeddingGenerator(
        override val id: String,
        val textField: String,
        val outputField: String,
        val model: String
    ) : Node {
        override fun inferOutputSchema(
            input: DataStream?,
            inputEncoding: Encoding?,
            context: InferenceContext
        ): InferenceResult {
            val stream = input ?: error("embeddingGenerator $id missing input")
            val inputSchema = stream.schema

            require(inputSchema is Schema.Struct) {
                "EmbeddingGenerator requires struct schema, got: ${inputSchema::class.simpleName}"
            }

            val hasTextField = inputSchema.value.any { it.name == textField }
            require(hasTextField) {
                "EmbeddingGenerator text field '$textField' not found in schema. Available fields: ${inputSchema.value.map { it.name }}"
            }

            val embeddingType = Schema.List(emptyList(), Schema.Float(0.0f))
            val newField = Schema.Field(outputField, embeddingType)
            val newFields = inputSchema.value + newField
            val outputStream = stream.copy(schema = Schema.Struct(newFields), originalAvroSchema = null)
            return InferenceResult(outputStream, inputEncoding ?: Encoding.AVRO)
        }

        override fun toProto(): Job.PipelineNode = Job.PipelineNode.newBuilder()
            .setId(id)
            .setEmbeddingGenerator(
                Job.EmbeddingGeneratorNode.newBuilder()
                    .setTextField(textField)
                    .setOutputField(outputField)
                    .setModel(model)
            )
            .build()

        override fun applyToShell(dataStreams: List<DataStream>, context: ExecutionContext): List<DataStream> {
            val service = context.embeddingGeneratorService ?: error("EmbeddingGeneratorService not available")
            return EmbeddingGeneratorExecution.applyToShell(this, dataStreams, service)
        }

        companion object {
            private const val DEFAULT_OUTPUT_FIELD = "embedding"
            private const val DEFAULT_MODEL = "text-embedding-3-small"

            fun fromProto(proto: Job.PipelineNode, context: FromProtoContext): EmbeddingGenerator {
                val e = proto.embeddingGenerator
                val outputField = if (e.outputField.isBlank()) DEFAULT_OUTPUT_FIELD else e.outputField
                val model = if (e.model.isBlank()) DEFAULT_MODEL else e.model
                return EmbeddingGenerator(proto.id, e.textField, outputField, model)
            }
        }
    }

    @Serializable
    data class OpenAiTransformer(
        override val id: String,
        val prompt: String,
        val outputField: String,
        val model: String
    ) : Node {
        override fun inferOutputSchema(
            input: DataStream?,
            inputEncoding: Encoding?,
            context: InferenceContext
        ): InferenceResult {
            val stream = input ?: error("openAiTransformer $id missing input")
            val inputSchema = stream.schema

            require(inputSchema is Schema.Struct) {
                "OpenAiTransformer requires struct schema, got: ${inputSchema::class.simpleName}"
            }

            val newField = Schema.Field(outputField, Schema.String.zeroValue)
            val newFields = inputSchema.value + newField
            val outputStream = stream.copy(schema = Schema.Struct(newFields), originalAvroSchema = null)
            return InferenceResult(outputStream, inputEncoding ?: Encoding.AVRO)
        }

        override fun toProto(): Job.PipelineNode = Job.PipelineNode.newBuilder()
            .setId(id)
            .setOpenAiTransformer(
                Job.OpenAiTransformerNode.newBuilder()
                    .setPrompt(prompt)
                    .setOutputField(outputField)
                    .setModel(model)
            )
            .build()

        override fun applyToShell(dataStreams: List<DataStream>, context: ExecutionContext): List<DataStream> {
            val service = context.openAiService ?: error("OpenAiService not available")
            return OpenAiTransformerExecution.applyToShell(this, dataStreams, service)
        }

        companion object {
            private const val DEFAULT_OUTPUT_FIELD = "ai_response"
            private const val DEFAULT_MODEL = "gpt-4o-mini"

            fun fromProto(proto: Job.PipelineNode, context: FromProtoContext): OpenAiTransformer {
                val t = proto.openAiTransformer
                val outputField = if (t.outputField.isBlank()) DEFAULT_OUTPUT_FIELD else t.outputField
                val model = if (t.model.isBlank()) DEFAULT_MODEL else t.model
                return OpenAiTransformer(proto.id, t.prompt, outputField, model)
            }
        }
    }
}
