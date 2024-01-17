package io.typestream.testing

import org.testcontainers.redpanda.RedpandaContainer

fun testConfigFile(testKafka: RedpandaContainer) = """
[grpc]
port=0
[sources.kafka.local]
bootstrapServers="${testKafka.bootstrapServers}"
schemaRegistry.url="${testKafka.schemaRegistryAddress}"
fsRefreshRate=1
""".trimIndent()
