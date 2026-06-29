package io.typestream.coroutine

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Computes the next delay between ticks. On success it resets to [base]; on failure it grows the
 * [current] delay by [factor], capped at [maxBackoff]. With the default [maxBackoff] == [base] this
 * is a no-op (fixed interval), preserving the original behaviour.
 */
internal fun nextTickDelay(
    current: Duration,
    base: Duration,
    factor: Double,
    maxBackoff: Duration,
    failed: Boolean,
): Duration = if (failed) (current * factor).coerceAtMost(maxBackoff) else base

fun CoroutineScope.tick(
    duration: Duration = 60.seconds,
    exceptionHandler: (Throwable) -> Unit = { throw it },
    maxBackoff: Duration = duration,
    factor: Double = 1.2,
    callback: () -> Unit,
) = this.launch {
    var currentDelay = duration
    while (isActive) {
        val failed = try {
            callback()
            false
        } catch (e: Throwable) {
            exceptionHandler(e)
            true
        }
        currentDelay = nextTickDelay(currentDelay, duration, factor, maxBackoff, failed)
        delay(currentDelay)
    }
}
