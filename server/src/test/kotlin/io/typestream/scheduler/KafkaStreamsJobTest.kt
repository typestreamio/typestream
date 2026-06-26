package io.typestream.scheduler

import io.github.oshai.kotlinlogging.KotlinLogging
import org.apache.kafka.streams.errors.StreamsUncaughtExceptionHandler
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class KafkaStreamsJobTest {

    @Test
    fun `uncaught exception handler shuts down the client`() {
        val handler = streamsUncaughtExceptionHandler("typestream-app-1", KotlinLogging.logger {})

        val response = handler.handle(RuntimeException("fatal stream error"))

        assertThat(response).isEqualTo(StreamsUncaughtExceptionHandler.StreamThreadExceptionResponse.SHUTDOWN_CLIENT)
    }
}
