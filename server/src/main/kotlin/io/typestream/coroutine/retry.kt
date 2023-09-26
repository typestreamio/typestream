package io.typestream.coroutine

import kotlinx.coroutines.delay

suspend fun retry(
    times: Int = Int.MAX_VALUE,
    initialDelay: Long = 100,
    maxDelay: Long = 10000,
    factor: Double = 1.2,
    block: suspend () -> Unit
) {
    var currentDelay = initialDelay
    repeat(times - 1) {
        try {
            block()
            return
        } catch (_: Exception) {

        }
        delay(currentDelay)
        currentDelay = (currentDelay * factor).toLong().coerceAtMost(maxDelay)
    }
    block()
}
