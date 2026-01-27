package io.typestream.server

import io.github.oshai.kotlinlogging.KotlinLogging
import io.grpc.Status
import io.grpc.StatusException
import io.typestream.compiler.types.DataStream
import io.typestream.compiler.vm.Vm
import io.typestream.grpc.state_query_service.StateQuery.GetAllValuesRequest
import io.typestream.grpc.state_query_service.StateQuery.GetValueRequest
import io.typestream.grpc.state_query_service.StateQuery.GetValueResponse
import io.typestream.grpc.state_query_service.StateQuery.KeyValuePair
import io.typestream.grpc.state_query_service.StateQuery.ListStoresRequest
import io.typestream.grpc.state_query_service.StateQuery.ListStoresResponse
import io.typestream.grpc.state_query_service.StateQueryServiceGrpcKt
import io.typestream.grpc.state_query_service.getValueResponse
import io.typestream.grpc.state_query_service.keyValuePair
import io.typestream.grpc.state_query_service.listStoresResponse
import io.typestream.grpc.state_query_service.storeInfo
import io.typestream.scheduler.KafkaStreamsJob
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.apache.kafka.streams.KafkaStreams
import org.apache.kafka.streams.StoreQueryParameters
import org.apache.kafka.streams.state.QueryableStoreTypes
import java.time.Instant

/**
 * gRPC service for querying state stores from running Kafka Streams jobs.
 *
 * This service enables interactive queries on KTable state stores created by count operations.
 * It provides endpoints to list available stores, retrieve all values, or get specific values by key.
 *
 * Note: State stores are only queryable when the underlying Kafka Streams application is in RUNNING state.
 * Stores from jobs that are rebalancing, starting, or stopped will not be accessible.
 *
 * ## Key Serialization
 *
 * Keys returned by [getAllValues] are JSON-serialized representations of the key's schema.
 * For example:
 * - A string key "hello" is serialized as `"hello"` (JSON string with quotes)
 * - A struct key is serialized as `{"field": "value"}` (JSON object)
 *
 * The [getValue] endpoint currently only supports simple string keys. For complex keys
 * (structs, lists, etc.), use [getAllValues] with appropriate filtering on the client side.
 *
 * @param vm The virtual machine instance providing access to the scheduler and running jobs
 */
class StateQueryService(private val vm: Vm) :
    StateQueryServiceGrpcKt.StateQueryServiceCoroutineImplBase() {

    private val logger = KotlinLogging.logger {}

    /**
     * Lists all queryable state stores from running Kafka Streams jobs.
     *
     * Only stores from jobs in RUNNING state are included. Stores that cannot be queried
     * (e.g., due to rebalancing) are omitted from the response with a warning logged.
     *
     * @param request Empty request message
     * @return Response containing information about all queryable stores including name, job ID, and approximate entry count
     */
    override suspend fun listStores(request: ListStoresRequest): ListStoresResponse = listStoresResponse {
        val runningJobs = vm.scheduler.ps()

        runningJobs.filterIsInstance<KafkaStreamsJob>().forEach { job ->
            val kafkaStreams = job.getKafkaStreams()
            // Only query stores when Kafka Streams is in RUNNING state
            if (kafkaStreams != null && kafkaStreams.state() == KafkaStreams.State.RUNNING) {
                job.getStateStoreNames().forEach { storeName ->
                    try {
                        // Use naming convention to determine store type
                        val approxCount = when {
                            storeName.contains("reduce") -> {
                                val store = kafkaStreams.store(
                                    StoreQueryParameters.fromNameAndType(
                                        storeName,
                                        QueryableStoreTypes.keyValueStore<DataStream, DataStream>()
                                    )
                                )
                                store.approximateNumEntries()
                            }
                            storeName.contains("windowed") -> {
                                // WindowStore doesn't support approximateNumEntries, return -1
                                // The store exists if we can query it
                                kafkaStreams.store(
                                    StoreQueryParameters.fromNameAndType(
                                        storeName,
                                        QueryableStoreTypes.windowStore<DataStream, Long>()
                                    )
                                )
                                -1L
                            }
                            else -> {
                                val store = kafkaStreams.store(
                                    StoreQueryParameters.fromNameAndType(
                                        storeName,
                                        QueryableStoreTypes.keyValueStore<DataStream, Long>()
                                    )
                                )
                                store.approximateNumEntries()
                            }
                        }
                        stores.add(storeInfo {
                            name = storeName
                            jobId = job.id
                            approximateCount = approxCount
                        })
                    } catch (e: Exception) {
                        // Skip stores that can't be queried (e.g., during state restoration)
                        logger.warn(e) { "Skipping store $storeName for job ${job.id}: not queryable" }
                    }
                }
            } else if (kafkaStreams != null) {
                logger.debug { "Skipping job ${job.id}: Kafka Streams state is ${kafkaStreams.state()}" }
            }
        }
    }

    /**
     * Streams all key-value pairs from a state store.
     *
     * Results are limited to prevent unbounded responses. The default limit is 100 entries.
     *
     * Note: The `from_key` field in the request is reserved for future pagination support
     * but is not currently implemented. All queries start from the beginning of the store.
     *
     * Keys are serialized as JSON strings. The exact format depends on the key's schema type:
     * - Primitive types (string, int, long): JSON primitive
     * - Struct types: JSON object with field names and values
     *
     * @param request Request containing the store name and optional limit
     * @return Flow of key-value pairs where keys are JSON-serialized and values are string representations
     * @throws StatusException with NOT_FOUND if the store doesn't exist or isn't queryable
     * @throws StatusException with UNAVAILABLE if the store exists but job isn't in RUNNING state
     * @throws StatusException with INTERNAL if an error occurs during iteration
     */
    override fun getAllValues(request: GetAllValuesRequest): Flow<KeyValuePair> = flow {
        val storeName = request.storeName
        val limit = if (request.limit > 0) request.limit else 100

        val (job, kafkaStreams) = findJobWithStore(storeName)
            ?: throw StatusException(Status.NOT_FOUND.withDescription("Store not found: $storeName"))

        // Verify Kafka Streams is in RUNNING state before querying
        if (kafkaStreams.state() != KafkaStreams.State.RUNNING) {
            throw StatusException(
                Status.UNAVAILABLE.withDescription(
                    "Store $storeName is not queryable: Kafka Streams state is ${kafkaStreams.state()}"
                )
            )
        }

        try {
            // Use naming convention to determine store type
            when {
                storeName.contains("reduce") -> {
                    val store = kafkaStreams.store(
                        StoreQueryParameters.fromNameAndType(
                            storeName,
                            QueryableStoreTypes.keyValueStore<DataStream, DataStream>()
                        )
                    )

                    store.all().use { iterator ->
                        var count = 0
                        try {
                            while (iterator.hasNext() && count < limit) {
                                val kv = iterator.next()
                                emit(keyValuePair {
                                    // Serialize key using schema's JSON representation
                                    key = kv.key.schema.toJsonElement().toString()
                                    // Value is a DataStream from reduce operations
                                    value = kv.value.schema.toJsonElement().toString()
                                })
                                count++
                            }
                        } catch (e: CancellationException) {
                            logger.debug { "getAllValues flow cancelled for store $storeName after $count entries" }
                            throw e
                        }
                    }
                }
                storeName.contains("windowed") -> {
                    val store = kafkaStreams.store(
                        StoreQueryParameters.fromNameAndType(
                            storeName,
                            QueryableStoreTypes.windowStore<DataStream, Long>()
                        )
                    )

                    // Fetch all windows from epoch to far future
                    val timeFrom = Instant.EPOCH
                    val timeTo = Instant.now().plusSeconds(86400 * 365) // 1 year in the future
                    store.fetchAll(timeFrom, timeTo).use { iterator ->
                        var count = 0
                        try {
                            while (iterator.hasNext() && count < limit) {
                                val kv = iterator.next()
                                val windowedKey = kv.key
                                val windowStart = Instant.ofEpochMilli(windowedKey.window().start())
                                val windowEnd = Instant.ofEpochMilli(windowedKey.window().end())
                                emit(keyValuePair {
                                    // Include window info in the key as JSON
                                    key = """{"key": ${windowedKey.key().schema.toJsonElement()}, "window": {"start": "$windowStart", "end": "$windowEnd"}}"""
                                    value = kv.value.toString()
                                })
                                count++
                            }
                        } catch (e: CancellationException) {
                            logger.debug { "getAllValues flow cancelled for store $storeName after $count entries" }
                            throw e
                        }
                    }
                }
                else -> {
                    val store = kafkaStreams.store(
                        StoreQueryParameters.fromNameAndType(
                            storeName,
                            QueryableStoreTypes.keyValueStore<DataStream, Long>()
                        )
                    )

                    store.all().use { iterator ->
                        var count = 0
                        try {
                            while (iterator.hasNext() && count < limit) {
                                val kv = iterator.next()
                                emit(keyValuePair {
                                    // Serialize key using schema's JSON representation
                                    key = kv.key.schema.toJsonElement().toString()
                                    // Value is a Long from count operations
                                    value = kv.value.toString()
                                })
                                count++
                            }
                        } catch (e: CancellationException) {
                            logger.debug { "getAllValues flow cancelled for store $storeName after $count entries" }
                            throw e
                        }
                    }
                }
            }
        } catch (e: StatusException) {
            throw e
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.error(e) { "Error querying store $storeName" }
            throw StatusException(Status.INTERNAL.withDescription("Error querying store: ${e.message}"))
        }
    }

    /**
     * Retrieves a single value from a state store by key.
     *
     * **Important Limitation**: This method currently only supports simple string keys.
     * The provided key string is used directly to construct a DataStream with Schema.String.
     * For stores with complex keys (structs, etc.), this method may not find matching entries.
     * Use [getAllValues] instead for stores with complex key types.
     *
     * @param request Request containing the store name and key to look up
     * @return Response indicating whether the key was found and its value (as a string)
     * @throws StatusException with NOT_FOUND if the store doesn't exist or isn't queryable
     * @throws StatusException with UNAVAILABLE if the store exists but job isn't in RUNNING state
     * @throws StatusException with INTERNAL if an error occurs during the lookup
     */
    override suspend fun getValue(request: GetValueRequest): GetValueResponse = getValueResponse {
        val storeName = request.storeName
        val keyStr = request.key

        val (job, kafkaStreams) = findJobWithStore(storeName)
            ?: throw StatusException(Status.NOT_FOUND.withDescription("Store not found: $storeName"))

        // Verify Kafka Streams is in RUNNING state before querying
        if (kafkaStreams.state() != KafkaStreams.State.RUNNING) {
            throw StatusException(
                Status.UNAVAILABLE.withDescription(
                    "Store $storeName is not queryable: Kafka Streams state is ${kafkaStreams.state()}"
                )
            )
        }

        try {
            // Note: This only works for string keys. The keyStr is wrapped in Schema.String,
            // so it won't match stores with struct or other complex key types.
            // For complex keys, clients should use getAllValues and filter client-side.
            val key = DataStream.fromString("", keyStr)

            // Use naming convention to determine store type
            if (storeName.contains("reduce")) {
                val store = kafkaStreams.store(
                    StoreQueryParameters.fromNameAndType(
                        storeName,
                        QueryableStoreTypes.keyValueStore<DataStream, DataStream>()
                    )
                )
                val result = store.get(key)
                found = result != null
                value = result?.schema?.toJsonElement()?.toString() ?: ""
            } else {
                val store = kafkaStreams.store(
                    StoreQueryParameters.fromNameAndType(
                        storeName,
                        QueryableStoreTypes.keyValueStore<DataStream, Long>()
                    )
                )
                val result = store.get(key)
                found = result != null
                value = result?.toString() ?: ""
            }
        } catch (e: StatusException) {
            throw e
        } catch (e: Exception) {
            logger.error(e) { "Error querying store $storeName for key $keyStr" }
            throw StatusException(Status.INTERNAL.withDescription("Error querying store: ${e.message}"))
        }
    }

    /**
     * Finds the Kafka Streams job that owns a given state store.
     *
     * @param storeName The name of the state store to find
     * @return A pair of the job and its KafkaStreams instance, or null if not found
     */
    private fun findJobWithStore(storeName: String): Pair<KafkaStreamsJob, KafkaStreams>? {
        val runningJobs = vm.scheduler.ps()

        for (job in runningJobs.filterIsInstance<KafkaStreamsJob>()) {
            if (job.getStateStoreNames().contains(storeName)) {
                val kafkaStreams = job.getKafkaStreams()
                if (kafkaStreams != null) {
                    return Pair(job, kafkaStreams)
                }
            }
        }
        return null
    }
}
