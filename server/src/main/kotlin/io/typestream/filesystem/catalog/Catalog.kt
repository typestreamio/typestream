package io.typestream.filesystem.catalog

import io.github.oshai.kotlinlogging.KotlinLogging
import io.typestream.compiler.types.DataStream
import io.typestream.compiler.types.Encoding
import io.typestream.compiler.types.datastream.fromAvroSchema
import io.typestream.config.SourcesConfig
import io.typestream.coroutine.tick
import io.typestream.filesystem.FileSystem
import io.typestream.kafka.schemaregistry.SchemaRegistryClient
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
        sourcesConfig.kafkaClustersConfig.clusters.forEach { (name, config) ->
            schemaRegistries[FileSystem.KAFKA_CLUSTERS_PREFIX + "/" + name] =
                SchemaRegistryClient(config.schemaRegistryUrl)
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
                is java.net.SocketException -> logger.warn(exception) { "catalog network failed" }
                else -> throw exception
            }
        }

        val scope = CoroutineScope(dispatcher + handler)
        // loop on subjects and default to string on fetching from the catalog is a better strategy
        sourcesConfig.kafkaClustersConfig.clusters.forEach { (name, config) ->
            scope.tick(config.fsRefreshRate.seconds, networkExceptionHandler) {
                val path = FileSystem.KAFKA_CLUSTERS_PREFIX + "/" + name
                logger.info { "fetching schemas for $path" }
                val schemaRegistryClient = schemaRegistries[path]

                requireNotNull(schemaRegistryClient) { "schema registry client not found for $name" }

                schemaRegistryClient.subjects().forEach { (subjectName, subject) ->
                    val topicPath = "$path/topics/${subjectName.replace("-value", "")}"

                    store[topicPath] =
                        Metadata(
                            DataStream.fromAvroSchema(topicPath, Parser().parse(subject.schema)),
                            Encoding.AVRO
                        )
                }
            }
        }
    }

    //In our definition of ready, we assume each cluster has got at least one path (e.g. one kafka topic)
    //so the catalog is ready when we have at least one path for each cluster
    fun isReady(): Boolean {
        val rootPaths = sourcesConfig.kafkaClustersConfig.clusters.keys.map { clusterName ->
            FileSystem.KAFKA_CLUSTERS_PREFIX + "/" + clusterName
        }

        return rootPaths.all { rootPath ->
            store.keys.any { it.startsWith(rootPath) }
        }
    }
}
