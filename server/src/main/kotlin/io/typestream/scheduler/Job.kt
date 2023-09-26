package io.typestream.scheduler

import kotlinx.coroutines.flow.Flow

sealed interface Job {
    val id: String

    fun remove()
    fun start()
    fun output(): Flow<String>
    fun stop()
    fun state(): State
    fun displayName() = "${id}\t${state()}"

    enum class State {
        STARTING,
        RUNNING,
        STOPPING,
        STOPPED,
        FAILED,
        UNKNOWN,
    }
}
