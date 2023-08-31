package io.typestream.scheduler

import io.typestream.compiler.Program
import kotlinx.coroutines.flow.Flow

sealed interface Job {
    val program: Program

    fun remove()
    fun startBackground()
    fun startForeground(): Flow<String>
    fun stop()
}
