package io.typestream.coroutine

import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

suspend fun tick(duration: Duration = 60.seconds, callback: () -> Unit) = coroutineScope {
    while (isActive) {
        callback()
        delay(duration)
    }
}
