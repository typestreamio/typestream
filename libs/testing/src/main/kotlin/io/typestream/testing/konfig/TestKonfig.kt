package io.typestream.testing.konfig

import io.typestream.testing.RedpandaContainerWrapper
import org.testcontainers.redpanda.RedpandaContainer
import java.io.ByteArrayInputStream

private fun kafkaProperties(testKafka: RedpandaContainer) = """
grpc.port=0
sources.kafka=local
sources.kafka.local.bootstrapServers=${testKafka.bootstrapServers}
sources.kafka.local.schemaRegistryUrl=${testKafka.schemaRegistryAddress}
sources.kafka.local.fsRefreshRate=1
        """.trimIndent()

fun testKonfig(testKafka: RedpandaContainerWrapper) =
    io.typestream.konfig.Konfig(ByteArrayInputStream(kafkaProperties(testKafka).toByteArray()))
