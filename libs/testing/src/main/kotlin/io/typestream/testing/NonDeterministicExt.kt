package io.typestream.testing

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext


private val logger = KotlinLogging.logger {}

suspend fun until(
    message: String = "condition",
    times: Int = 10,
    block: suspend () -> Unit,
) {
    repeat(times) {
        try {
            block()
            return
        } catch (e: Throwable) {
            logger.info { "waiting for $message... (${it + 1}/$times) ${e.message}" }
            withContext(Dispatchers.Default) {
                delay(1_000)
            }
        }
    }
}
