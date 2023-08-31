package io.typestream.kafka

import org.apache.kafka.streams.StreamsBuilder

class StreamsBuilderWrapper(val config: Map<String, Any>) : StreamsBuilder()
