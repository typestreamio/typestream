package io.typestream.scheduler

import io.github.oshai.kotlinlogging.KotlinLogging
import io.typestream.compiler.Program
import io.typestream.compiler.kafka.KafkaStreamSource
import io.typestream.compiler.node.Node
import io.typestream.compiler.types.DataStream
import io.typestream.config.KafkaConfig
import io.typestream.embedding.EmbeddingGeneratorService
import io.typestream.geoip.GeoIpService
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
    private val embeddingGeneratorService: EmbeddingGeneratorService
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
            require(source is Node.StreamSource) { "source node must be a StreamSource" }

            val kafkaStreamSource = KafkaStreamSource(source, streamsBuilder, geoIpService, textExtractorService, embeddingGeneratorService)

            sourceNode.walk { currentNode ->
                when (currentNode.ref) {
                    is Node.Count -> {
                        val storeName = "${program.id}-count-store-$countStoreIndex"
                        countStoreIndex++
                        kafkaStreamSource.count(storeName)
                        kafkaStreamSource.getCountStoreName()?.let { stateStoreNames.add(it) }
                    }
                    is Node.ReduceLatest -> {
                        val storeName = "${program.id}-reduce-store-$reduceStoreIndex"
                        reduceStoreIndex++
                        kafkaStreamSource.reduceLatest(storeName)
                        stateStoreNames.add(storeName)
                    }
                    is Node.Filter -> kafkaStreamSource.filter(currentNode.ref)
                    is Node.Group -> kafkaStreamSource.group(currentNode.ref)
                    is Node.Join -> kafkaStreamSource.join(currentNode.ref)
                    is Node.Map -> kafkaStreamSource.map(currentNode.ref)
                    is Node.Each -> kafkaStreamSource.each(currentNode.ref)
                    is Node.GeoIp -> kafkaStreamSource.geoIp(currentNode.ref)
                    is Node.TextExtractor -> kafkaStreamSource.textExtract(currentNode.ref)
                    is Node.EmbeddingGenerator -> kafkaStreamSource.embeddingGenerate(currentNode.ref)
                    is Node.NoOp -> {}
                    is Node.StreamSource -> {}
                    is Node.Sink -> kafkaStreamSource.to(currentNode.ref)
                    is Node.Inspector -> kafkaStreamSource.toInspector(currentNode.ref, program.id)
                    is Node.ShellSource -> error("cannot resolve ShellSource node")
                }
            }
        }

        return streamsBuilder.build()
    }

    override fun output() = output("${program.id}-stdout")

    fun output(topic: String) = flow {
        // Retry until job is running (may be starting up)
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
}
