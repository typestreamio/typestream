package io.typestream.coroutine

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

fun CoroutineScope.tick(
    duration: Duration = 60.seconds,
    exceptionHandler: (Throwable) -> Unit = { throw it },
    callback: () -> Unit,
) = this.launch {
    while (isActive) {
        try {
            callback()
        } catch (e: Throwable) {
            exceptionHandler(e)
        }
        delay(duration)
    }
}
