package io.typestream.connectors.kafka

import org.apache.avro.specific.SpecificRecord

interface MessageSender : AutoCloseable {
    fun send(key: String, value: SpecificRecord)
}
