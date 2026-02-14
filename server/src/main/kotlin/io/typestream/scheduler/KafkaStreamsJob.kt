package io.typestream.scheduler

import io.github.oshai.kotlinlogging.KotlinLogging
import io.typestream.compiler.Program
import io.typestream.compiler.kafka.KafkaStreamSource
import io.typestream.compiler.node.Node
import io.typestream.compiler.node.NodeCount
import io.typestream.compiler.node.NodeEach
import io.typestream.compiler.node.NodeEmbeddingGenerator
import io.typestream.compiler.node.NodeFilter
import io.typestream.compiler.node.NodeGeoIp
import io.typestream.compiler.node.NodeGroup
import io.typestream.compiler.node.NodeInspector
import io.typestream.compiler.node.NodeJoin
import io.typestream.compiler.node.NodeMap
import io.typestream.compiler.node.NodeNoOp
import io.typestream.compiler.node.NodeOpenAiTransformer
import io.typestream.compiler.node.NodeReduceLatest
import io.typestream.compiler.node.NodeShellSource
import io.typestream.compiler.node.NodeSink
import io.typestream.compiler.node.NodeStreamSource
import io.typestream.compiler.node.NodeTextExtractor
import io.typestream.compiler.node.NodeWindowedCount
import io.typestream.compiler.types.DataStream
import io.typestream.config.KafkaConfig
import io.typestream.embedding.EmbeddingGeneratorService
import io.typestream.geoip.GeoIpService
import io.typestream.openai.OpenAiService
import io.typestream.textextractor.TextExtractorService
import io.typestream.coroutine.retry
import io.typestream.kafka.DataStreamSerde
import io.typestream.kafka.StreamsBuilderWrapper
import kotlinx.coroutines.flow.flow
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.common.config.SaslConfigs
import org.apache.kafka.streams.KafkaStreams
import org.apache.kafka.streams.StreamsConfig
import org.apache.kafka.streams.Topology
import java.util.Properties
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaDuration

/**
 * A key-value record from a Kafka topic, used for preview streaming.
 */
data class PreviewRecord(val key: String, val value: String)


/**
 * A Kafka Streams-based job that executes a compiled TypeStream program.
 *
 * This class manages the lifecycle of a Kafka Streams application, including building
 * the processing topology, starting/stopping the streams, and tracking state stores.
 *
 * ## State Store Naming
 *
 * State stores created by count operations are named using the pattern:
 * `{program.id}-count-store-{index}` where index is assigned sequentially as count
 * operations are encountered during topology building.
 *
 * **Important**: Store names are tied to the position of count operations in the program
 * graph. If a program is modified (e.g., count operations added/removed/reordered),
 * the store names may change on the next deployment. This can affect:
 * - Interactive query clients that rely on specific store names
 * - Kafka Streams state restoration (stores may be rebuilt from scratch)
 *
 * Consider using stable, user-defined store names in the future if this becomes an issue.
 *
 * @param id Unique identifier for this job
 * @param program The compiled TypeStream program to execute
 * @param kafkaConfig Kafka cluster configuration
 * @param geoIpService Service for GeoIP lookups in transformations
 */
class KafkaStreamsJob(
    override val id: String,
    val program: Program,
    private val kafkaConfig: KafkaConfig,
    private val geoIpService: GeoIpService,
    private val textExtractorService: TextExtractorService,
    private val embeddingGeneratorService: EmbeddingGeneratorService,
    private val openAiService: OpenAiService
) : Job {
    private var running: Boolean = false
    private val logger = KotlinLogging.logger {}
    private var kafkaStreams: KafkaStreams? = null
    override var startTime: Long = 0L
        private set

    private val stateStoreNames = mutableListOf<String>()

    fun getKafkaStreams(): KafkaStreams? = kafkaStreams

    fun getStateStoreNames(): List<String> = stateStoreNames.toList()

    private fun buildTopology(): Topology {
        val streamsBuilder = StreamsBuilderWrapper(config())
        var countStoreIndex = 0
        var reduceStoreIndex = 0

        program.graph.children.forEach { sourceNode ->
            val source = sourceNode.ref
            require(source is NodeStreamSource) { "source node must be a StreamSource" }

            val kafkaStreamSource = KafkaStreamSource(source, streamsBuilder, geoIpService, textExtractorService, embeddingGeneratorService, openAiService)

            sourceNode.walk { currentNode ->
                when (currentNode.ref) {
                    is NodeCount -> {
                        val storeName = "${program.id}-count-store-$countStoreIndex"
                        countStoreIndex++
                        kafkaStreamSource.count(storeName)
                        kafkaStreamSource.getCountStoreName()?.let { stateStoreNames.add(it) }
                    }
                    is NodeWindowedCount -> {
                        val storeName = "${program.id}-windowed-count-store-$countStoreIndex"
                        countStoreIndex++
                        val windowSize = java.time.Duration.ofSeconds(currentNode.ref.windowSizeSeconds)
                        kafkaStreamSource.windowedCount(storeName, windowSize)
                        kafkaStreamSource.getCountStoreName()?.let { stateStoreNames.add(it) }
                    }
                    is NodeReduceLatest -> {
                        val storeName = "${program.id}-reduce-store-$reduceStoreIndex"
                        reduceStoreIndex++
                        kafkaStreamSource.reduceLatest(storeName)
                        stateStoreNames.add(storeName)
                    }
                    is NodeFilter -> kafkaStreamSource.filter(currentNode.ref)
                    is NodeGroup -> kafkaStreamSource.group(currentNode.ref)
                    is NodeJoin -> kafkaStreamSource.join(currentNode.ref)
                    is NodeMap -> kafkaStreamSource.map(currentNode.ref)
                    is NodeEach -> kafkaStreamSource.each(currentNode.ref)
                    is NodeGeoIp -> kafkaStreamSource.geoIp(currentNode.ref)
                    is NodeTextExtractor -> kafkaStreamSource.textExtract(currentNode.ref)
                    is NodeEmbeddingGenerator -> kafkaStreamSource.embeddingGenerate(currentNode.ref)
                    is NodeOpenAiTransformer -> kafkaStreamSource.openAiTransform(currentNode.ref)
                    is NodeNoOp -> {}
                    is NodeStreamSource -> {}
                    is NodeSink -> kafkaStreamSource.to(currentNode.ref)
                    is NodeInspector -> kafkaStreamSource.toInspector(currentNode.ref, program.id)
                    is NodeShellSource -> error("cannot resolve ShellSource node")
                }
            }
        }

        return streamsBuilder.build()
    }

    override fun output() = output("${program.id}-stdout")

    /**
     * Creates a real KafkaConsumer to poll records from the given topic.
     * Used by [JobService.streamPreview] to stream inspector output to the UI.
     * The consumer reads from earliest offset and emits JSON-serialized records.
     */
    fun output(topic: String) = flow {
        retry {
            require(isRunning()) { "cannot get output of a non-running job" }
        }

        val consumer = KafkaConsumer<DataStream, DataStream>(consumerProps(topic))
        consumer.subscribe(listOf(topic))

        while (isRunning()) {
            val records = consumer.poll(1.seconds.toJavaDuration())
            records.forEach {
                val valueElement = it.value().schema.toJsonElement()
                emit(valueElement.toString())
            }
        }

        if (kafkaStreams?.state()?.equals(KafkaStreams.State.ERROR) == true) {
            emit("${program.id} exited with error")
        }
        consumer.close()
    }

    /**
     * Creates a real KafkaConsumer to poll records from the given topic, returning both key and value.
     * Used by [JobService.streamPreview] to stream inspector output with keys to the UI.
     * The consumer reads from earliest offset and emits key-value pairs.
     */
    fun outputWithKey(topic: String) = flow {
        retry {
            require(isRunning()) { "cannot get output of a non-running job" }
        }

        val consumer = KafkaConsumer<DataStream, DataStream>(consumerProps(topic))
        consumer.subscribe(listOf(topic))

        while (isRunning()) {
            val records = consumer.poll(1.seconds.toJavaDuration())
            records.forEach {
                val keyElement = it.key()?.schema?.toJsonElement()?.toString() ?: ""
                val valueElement = it.value().schema.toJsonElement().toString()
                emit(PreviewRecord(keyElement, valueElement))
            }
        }

        consumer.close()
    }

    override fun start() {
        val topology = buildTopology()
        kafkaStreams = KafkaStreams(topology, StreamsConfig(config()))
        logger.debug { topology.describe().toString() }

        logger.info { "starting ${program.id}" }
        startTime = System.currentTimeMillis()
        kafkaStreams?.start()
        running = true
    }

    private fun config(): Map<String, Any> {
        val props = mutableMapOf<String, Any>()
        props[StreamsConfig.APPLICATION_ID_CONFIG] = program.id
        props[StreamsConfig.BOOTSTRAP_SERVERS_CONFIG] = kafkaConfig.bootstrapServers
        props[StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG] = DataStreamSerde::class.java
        props[StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG] = DataStreamSerde::class.java

        //TODO make this a config
        props[StreamsConfig.COMMIT_INTERVAL_MS_CONFIG] = 100
        props["schema.registry.url"] = kafkaConfig.schemaRegistry.url
        kafkaConfig.schemaRegistry.userInfo?.let {
            props["schema.registry.userInfo"] = it
        }

        kafkaConfig.saslConfig?.let {
            props[StreamsConfig.SECURITY_PROTOCOL_CONFIG] = "SASL_SSL"
            props[SaslConfigs.SASL_MECHANISM] = it.mechanism
            props[SaslConfigs.SASL_JAAS_CONFIG] = it.jaasConfig
        }

        return props
    }

    private fun consumerProps(topic: String): Properties {
        val props = Properties()
        props[ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG] = kafkaConfig.bootstrapServers
        props[ConsumerConfig.GROUP_ID_CONFIG] = "$topic-consumer"
        props[ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG] = DataStreamSerde::class.java
        props[ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG] = DataStreamSerde::class.java
        props[ConsumerConfig.AUTO_OFFSET_RESET_CONFIG] = "earliest"
        return props
    }

    override fun stop() {
        logger.info { "stopping ${program.id}" }
        running = false
        kafkaStreams?.close()
    }

    override fun remove() {
        require(!running) { "cannot remove a running Kafka Streams app" }
        logger.info { "removing ${program.id}" }
        kafkaStreams?.cleanUp()
    }

    private fun isRunning() = running && ((kafkaStreams?.state()?.hasNotStarted()
        ?: false) || (kafkaStreams?.state()?.isRunningOrRebalancing ?: false))

    override fun state(): Job.State = when (kafkaStreams?.state()) {
        KafkaStreams.State.CREATED -> Job.State.STARTING
        //TODO not quite good enough but does the job (no pun intended) for now
        KafkaStreams.State.REBALANCING -> Job.State.UNKNOWN
        KafkaStreams.State.RUNNING -> Job.State.RUNNING
        KafkaStreams.State.PENDING_SHUTDOWN -> Job.State.STOPPING
        KafkaStreams.State.NOT_RUNNING -> Job.State.STOPPED
        KafkaStreams.State.PENDING_ERROR -> Job.State.FAILED
        KafkaStreams.State.ERROR -> Job.State.FAILED
        null -> Job.State.UNKNOWN
    }

    /**
     * Returns throughput metrics from Kafka Streams.
     *
     * Uses built-in metrics:
     * - stream-thread-metrics: process-rate, process-total
     * - consumer-fetch-manager-metrics: bytes-consumed-rate, bytes-consumed-total
     */
    override fun throughput(): Job.Throughput {
        val streams = kafkaStreams ?: return Job.Throughput()
        val allMetrics = streams.metrics()

        var messagesPerSecond = 0.0
        var totalMessages = 0L
        var bytesPerSecond = 0.0
        var totalBytes = 0L

        allMetrics.forEach { (name, metric) ->
            val groupName = name.group()
            val metricName = name.name()

            when {
                // Stream thread metrics for message processing rate
                groupName == "stream-thread-metrics" && metricName == "process-rate" -> {
                    val value = metric.metricValue()
                    if (value is Number) messagesPerSecond += value.toDouble()
                }
                groupName == "stream-thread-metrics" && metricName == "process-total" -> {
                    val value = metric.metricValue()
                    if (value is Number) totalMessages += value.toLong()
                }
                // Consumer fetch manager metrics for bytes consumed
                groupName == "consumer-fetch-manager-metrics" && metricName == "bytes-consumed-rate" -> {
                    val value = metric.metricValue()
                    if (value is Number) bytesPerSecond += value.toDouble()
                }
                groupName == "consumer-fetch-manager-metrics" && metricName == "bytes-consumed-total" -> {
                    val value = metric.metricValue()
                    if (value is Number) totalBytes += value.toLong()
                }
            }
        }

        return Job.Throughput(
            messagesPerSecond = messagesPerSecond,
            totalMessages = totalMessages,
            bytesPerSecond = bytesPerSecond,
            totalBytes = totalBytes
        )
    }
}
