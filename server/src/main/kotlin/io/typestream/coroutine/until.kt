package io.typestream.coroutine

import kotlinx.coroutines.delay
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

suspend fun until(duration: Duration = 10.seconds, callback: () -> Boolean) {
    while (!callback()) {
        delay(duration)
    }
}
