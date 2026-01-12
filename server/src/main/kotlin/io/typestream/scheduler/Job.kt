package io.typestream.scheduler

import kotlinx.coroutines.flow.Flow

sealed interface Job {
    val id: String
    val startTime: Long // Unix timestamp in milliseconds

    fun remove()
    fun start()
    fun output(): Flow<String>
    fun stop()
    fun state(): State
    fun displayName() = "${id}\t${state()}"
    fun throughput(): Throughput

    data class Throughput(
        val messagesPerSecond: Double, // Messages per second (rate over recent window)
        val totalMessages: Long        // Total messages processed
    )

    enum class State {
        STARTING,
        RUNNING,
        STOPPING,
        STOPPED,
        FAILED,
        UNKNOWN,
    }
}
