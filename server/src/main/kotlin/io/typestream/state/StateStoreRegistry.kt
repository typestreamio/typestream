package io.typestream.state

import org.apache.kafka.streams.KafkaStreams
import java.util.concurrent.ConcurrentHashMap

data class StateStoreInfo(
    val name: String,
    val jobId: String,
    val kafkaStreams: KafkaStreams,
    val storeName: String  // Internal Kafka Streams store name
)

class StateStoreRegistry {
    private val stores = ConcurrentHashMap<String, StateStoreInfo>()

    fun register(name: String, info: StateStoreInfo) {
        stores[name] = info
    }

    fun unregister(name: String) {
        stores.remove(name)
    }

    fun get(name: String): StateStoreInfo? = stores[name]

    fun list(): List<StateStoreInfo> = stores.values.toList()
}
