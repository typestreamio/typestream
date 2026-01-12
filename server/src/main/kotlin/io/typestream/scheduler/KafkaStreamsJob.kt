package io.typestream.scheduler

import io.github.oshai.kotlinlogging.KotlinLogging
import io.typestream.compiler.Program
import io.typestream.compiler.kafka.KafkaStreamSource
import io.typestream.compiler.node.Node
import io.typestream.compiler.types.DataStream
import io.typestream.config.KafkaConfig
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


class KafkaStreamsJob(override val id: String, val program: Program, private val kafkaConfig: KafkaConfig) : Job {
    private var running: Boolean = false
    private val logger = KotlinLogging.logger {}
    private var kafkaStreams: KafkaStreams? = null
    override var startTime: Long = 0L
        private set

    private fun buildTopology(): Topology {
        val streamsBuilder = StreamsBuilderWrapper(config())

        program.graph.children.forEach { sourceNode ->
            val source = sourceNode.ref
            require(source is Node.StreamSource) { "source node must be a StreamSource" }

            val kafkaStreamSource = KafkaStreamSource(source, streamsBuilder)

            sourceNode.walk { currentNode ->
                when (currentNode.ref) {
                    is Node.Count -> kafkaStreamSource.count()
                    is Node.Filter -> kafkaStreamSource.filter(currentNode.ref)
                    is Node.Group -> kafkaStreamSource.group(currentNode.ref)
                    is Node.Join -> kafkaStreamSource.join(currentNode.ref)
                    is Node.Map -> kafkaStreamSource.map(currentNode.ref)
                    is Node.Each -> kafkaStreamSource.each(currentNode.ref)
                    is Node.NoOp -> {}
                    is Node.StreamSource -> {}
                    is Node.Sink -> kafkaStreamSource.to(currentNode.ref)
                    is Node.ShellSource -> error("cannot resolve ShellSource node")
                }
            }
        }

        return streamsBuilder.build()
    }

    override fun output() = flow {
        // we retry here because we may be trying to get output before the job has started
        retry {
            require(isRunning()) { "cannot get output of a non-running job" }
        }
        val consumer = KafkaConsumer<DataStream, DataStream>(consumerProps())
        consumer.subscribe(listOf("${program.id}-stdout"))

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

    private fun consumerProps(): Properties {
        val props = Properties()
        props[ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG] = kafkaConfig.bootstrapServers
        props[ConsumerConfig.GROUP_ID_CONFIG] = "${program.id}-stdout-consumer"
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

    override fun throughput(): Job.Throughput {
        val streams = kafkaStreams ?: return Job.Throughput(0.0, 0)

        val metrics = streams.metrics()

        // Find process-rate metric (messages processed per second)
        // This is a stream-thread level metric
        var processRate = 0.0
        var totalRecords = 0L

        for ((name, metric) in metrics) {
            // process-rate gives us the rate of records processed per second
            if (name.name() == "process-rate" && name.group() == "stream-thread-metrics") {
                val value = metric.metricValue()
                if (value is Number) {
                    processRate += value.toDouble()
                }
            }
            // process-total gives us total records processed
            if (name.name() == "process-total" && name.group() == "stream-thread-metrics") {
                val value = metric.metricValue()
                if (value is Number) {
                    totalRecords += value.toLong()
                }
            }
        }

        return Job.Throughput(processRate, totalRecords)
    }

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
