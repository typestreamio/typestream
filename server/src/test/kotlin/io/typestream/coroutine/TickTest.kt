package io.typestream.coroutine

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.seconds

internal class TickTest {

    @Test
    fun `resets to base delay on success`() {
        val next = nextTickDelay(current = 8.seconds, base = 1.seconds, factor = 2.0, maxBackoff = 10.seconds, failed = false)

        assertThat(next).isEqualTo(1.seconds)
    }

    @Test
    fun `grows delay by factor on failure`() {
        val next = nextTickDelay(current = 1.seconds, base = 1.seconds, factor = 2.0, maxBackoff = 10.seconds, failed = true)

        assertThat(next).isEqualTo(2.seconds)
    }

    @Test
    fun `caps backoff at maxBackoff`() {
        val next = nextTickDelay(current = 8.seconds, base = 1.seconds, factor = 2.0, maxBackoff = 10.seconds, failed = true)

        assertThat(next).isEqualTo(10.seconds)
    }
}
