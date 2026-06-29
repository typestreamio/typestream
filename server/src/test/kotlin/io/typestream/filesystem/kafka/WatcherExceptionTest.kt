package io.typestream.filesystem.kafka

import org.apache.kafka.common.KafkaException
import org.apache.kafka.common.errors.TimeoutException
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.net.ConnectException
import java.util.concurrent.ExecutionException

internal class WatcherExceptionTest {

    @Test
    fun `broker timeouts are transient`() {
        assertThat(isTransientBrokerException(TimeoutException("timed out"))).isTrue()
    }

    @Test
    fun `kafka exceptions are transient`() {
        assertThat(isTransientBrokerException(KafkaException("no brokers"))).isTrue()
    }

    @Test
    fun `connect failures are transient`() {
        assertThat(isTransientBrokerException(ConnectException("connection refused"))).isTrue()
    }

    @Test
    fun `unwraps wrapped causes`() {
        assertThat(isTransientBrokerException(ExecutionException(TimeoutException("timed out")))).isTrue()
    }

    @Test
    fun `unexpected exceptions are not transient`() {
        assertThat(isTransientBrokerException(IllegalStateException("boom"))).isFalse()
    }
}
