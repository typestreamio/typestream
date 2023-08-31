package io.typestream.scheduler

import io.github.oshai.kotlinlogging.KotlinLogging
import io.typestream.compiler.Program
import io.typestream.compiler.kafka.KafkaStreamSource
import io.typestream.compiler.node.Node
import io.typestream.compiler.types.DataStream
import io.typestream.config.KafkaConfig
import io.typestream.kafka.DataStreamSerde
import io.typestream.kafka.StreamsBuilderWrapper
import kotlinx.coroutines.flow.flow
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.streams.KafkaStreams
import org.apache.kafka.streams.StreamsConfig
import org.apache.kafka.streams.Topology
import java.util.Properties
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaDuration


class KafkaStreamsJob(override val program: Program, private val kafkaConfig: KafkaConfig) : Job {
    private var running: Boolean = false
    private val logger = KotlinLogging.logger {}
    private var kafkaStreams: KafkaStreams? = null

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
                    is Node.NoOp -> {}
                    is Node.StreamSource -> {}
                    is Node.Sink -> kafkaStreamSource.to(currentNode.ref)
                    is Node.ShellSource -> error("cannot resolve ShellSource node")
                }
            }
        }

        return streamsBuilder.build()
    }

    override fun startForeground() = flow {
        start()

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

    override fun startBackground() {
        start()
    }

    private fun start() {
        val topology = buildTopology()
        kafkaStreams = KafkaStreams(topology, StreamsConfig(config()))
        logger.debug { topology.describe().toString() }

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
        props["schema.registry.url"] = kafkaConfig.schemaRegistryUrl

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
        logger.info { "stopping $program.id" }
        running = false
        kafkaStreams?.close()
    }

    override fun remove() {
        require(!running) { "cannot remove a running Kafka Streams app" }
        kafkaStreams?.cleanUp()
    }

    private fun isRunning() = running && ((kafkaStreams?.state()?.hasNotStarted()
        ?: false) || (kafkaStreams?.state()?.isRunningOrRebalancing ?: false))

    override fun toString() = buildString {
        append(program.id)
        append(" state: ${kafkaStreams?.state()}")
    }
}
