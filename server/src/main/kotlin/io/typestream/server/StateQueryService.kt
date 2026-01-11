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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.apache.kafka.streams.StoreQueryParameters
import org.apache.kafka.streams.state.QueryableStoreTypes

class StateQueryService(private val vm: Vm) :
    StateQueryServiceGrpcKt.StateQueryServiceCoroutineImplBase() {

    private val logger = KotlinLogging.logger {}

    override suspend fun listStores(request: ListStoresRequest): ListStoresResponse = listStoresResponse {
        val runningJobs = vm.scheduler.ps()

        runningJobs.filterIsInstance<KafkaStreamsJob>().forEach { job ->
            val kafkaStreams = job.getKafkaStreams()
            if (kafkaStreams != null) {
                job.getStateStoreNames().forEach { storeName ->
                    try {
                        val store = kafkaStreams.store(
                            StoreQueryParameters.fromNameAndType(
                                storeName,
                                QueryableStoreTypes.keyValueStore<DataStream, Long>()
                            )
                        )
                        stores.add(storeInfo {
                            name = storeName
                            jobId = job.id
                            approximateCount = store.approximateNumEntries()
                        })
                    } catch (e: Exception) {
                        logger.warn(e) { "Could not query store $storeName for job ${job.id}" }
                        // Still add the store info even if we can't get the count
                        stores.add(storeInfo {
                            name = storeName
                            jobId = job.id
                            approximateCount = -1
                        })
                    }
                }
            }
        }
    }

    override fun getAllValues(request: GetAllValuesRequest): Flow<KeyValuePair> = flow {
        val storeName = request.storeName
        val limit = if (request.limit > 0) request.limit else 100

        val (job, kafkaStreams) = findJobWithStore(storeName)
            ?: throw StatusException(Status.NOT_FOUND.withDescription("Store not found: $storeName"))

        try {
            val store = kafkaStreams.store(
                StoreQueryParameters.fromNameAndType(
                    storeName,
                    QueryableStoreTypes.keyValueStore<DataStream, Long>()
                )
            )

            store.all().use { iterator ->
                var count = 0
                while (iterator.hasNext() && count < limit) {
                    val kv = iterator.next()
                    emit(keyValuePair {
                        key = kv.key.toString()
                        value = kv.value.toString()
                    })
                    count++
                }
            }
        } catch (e: Exception) {
            logger.error(e) { "Error querying store $storeName" }
            throw StatusException(Status.INTERNAL.withDescription("Error querying store: ${e.message}"))
        }
    }

    override suspend fun getValue(request: GetValueRequest): GetValueResponse = getValueResponse {
        val storeName = request.storeName
        val keyStr = request.key

        val (job, kafkaStreams) = findJobWithStore(storeName)
            ?: throw StatusException(Status.NOT_FOUND.withDescription("Store not found: $storeName"))

        try {
            val store = kafkaStreams.store(
                StoreQueryParameters.fromNameAndType(
                    storeName,
                    QueryableStoreTypes.keyValueStore<DataStream, Long>()
                )
            )

            // Create a DataStream key from the string
            val key = DataStream.fromString("", keyStr)
            val result = store.get(key)

            found = result != null
            value = result?.toString() ?: ""
        } catch (e: Exception) {
            logger.error(e) { "Error querying store $storeName for key $keyStr" }
            throw StatusException(Status.INTERNAL.withDescription("Error querying store: ${e.message}"))
        }
    }

    private fun findJobWithStore(storeName: String): Pair<KafkaStreamsJob, org.apache.kafka.streams.KafkaStreams>? {
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
