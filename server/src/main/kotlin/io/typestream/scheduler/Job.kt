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
    fun throughput(): Throughput
    fun displayName() = "${id}\t${state()}"

    /**
     * Throughput metrics for a job.
     *
     * @property messagesPerSecond Current processing rate (messages/sec)
     * @property totalMessages Total messages processed since job start
     * @property bytesPerSecond Current bandwidth consumption (bytes/sec)
     * @property totalBytes Total bytes processed since job start
     */
    data class Throughput(
        val messagesPerSecond: Double = 0.0,
        val totalMessages: Long = 0L,
        val bytesPerSecond: Double = 0.0,
        val totalBytes: Long = 0L
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
