package io.typestream.filesystem.catalog

import io.github.oshai.kotlinlogging.KotlinLogging
import io.typestream.compiler.types.DataStream
import io.typestream.compiler.types.Encoding
import io.typestream.compiler.types.datastream.fromAvroSchema
import io.typestream.compiler.types.datastream.fromProtoSchema
import io.typestream.config.SourcesConfig
import io.typestream.coroutine.tick
import io.typestream.filesystem.FileSystem
import io.typestream.kafka.protobuf.ProtoParser
import io.typestream.kafka.schemaregistry.SchemaRegistryClient
import io.typestream.kafka.schemaregistry.SchemaType
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.supervisorScope
import org.apache.avro.Schema.Parser
import java.util.Collections
import kotlin.time.Duration.Companion.seconds


class Catalog(private val sourcesConfig: SourcesConfig, private val dispatcher: CoroutineDispatcher) {
    private val logger = KotlinLogging.logger {}
    private val store = Collections.synchronizedMap(mutableMapOf<String, Metadata>())
    private val schemaRegistries = mutableMapOf<String, SchemaRegistryClient>()

    init {
        sourcesConfig.kafka.forEach { (name, config) ->
            schemaRegistries[FileSystem.KAFKA_CLUSTERS_PREFIX + "/" + name] =
                SchemaRegistryClient(config.schemaRegistry)
        }
    }

    operator fun get(path: String) = store[path]

    // TODO redesign. This is an *extremely* naive implementation, just to get things going.
    // We're also ignoring keys for now.
    // Ideally what we want to do here is load subjects at startup and then watch for changes (via the _schemas topic)
    suspend fun watch() = supervisorScope {
        val handler = CoroutineExceptionHandler { _, exception ->
            logger.error(exception) { "catalog failed" }
        }

        val networkExceptionHandler: (Throwable) -> Unit = { exception ->
            when (exception) {
                is java.net.SocketException -> logger.info(exception) { "catalog network failed" }
                else -> throw exception
            }
        }

        val scope = CoroutineScope(dispatcher + handler)
        // loop on subjects and default to string on fetching from the catalog is a better strategy
        sourcesConfig.kafka.forEach { (name, config) ->
            scope.tick(config.fsRefreshRate.seconds, networkExceptionHandler) {
                refreshRegistry(name)
            }
        }
    }

    private fun refreshRegistry(name: String) {
        val path = FileSystem.KAFKA_CLUSTERS_PREFIX + "/" + name
        logger.info { "fetching schemas for $path" }
        val schemaRegistryClient = schemaRegistries[path]

        requireNotNull(schemaRegistryClient) { "schema registry client not found for $path" }

        schemaRegistryClient.subjects().forEach { (subjectName, subject) ->
            val topicPath = "$path/topics/${subjectName.replace("-value", "")}"

            try {
                store[topicPath] = when (subject.schemaType) {
                    SchemaType.AVRO -> Metadata(
                        DataStream.fromAvroSchema(topicPath, Parser().parse(subject.schema)),
                        Encoding.AVRO
                    )

                    SchemaType.PROTOBUF -> Metadata(
                        DataStream.fromProtoSchema(topicPath, ProtoParser.parse(subject.schema)),
                        Encoding.PROTOBUF
                    )

                    else -> error("unsupported schema type ${subject.schemaType} for $topicPath")
                }
            } catch (e: Exception) {
                logger.error(e) { "failed to fetch schema for $topicPath" }
            }
        }
    }

    fun refresh() {
        sourcesConfig.kafka.keys.forEach(::refreshRegistry)
    }
}
