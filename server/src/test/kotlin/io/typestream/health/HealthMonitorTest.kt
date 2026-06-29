package io.typestream.health

import io.grpc.health.v1.HealthCheckResponse.ServingStatus
import io.grpc.services.HealthStatusManager
import io.typestream.scheduler.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes

internal class HealthMonitorTest {

    private fun monitor(
        grace: Duration = 5.minutes,
        clock: () -> Long,
        jobStates: () -> List<Pair<String, Job.State>>,
    ) = HealthMonitor(jobStates, HealthStatusManager(), graceWindow = grace, clock = clock)

    @Test
    fun `serving when all jobs are running`() {
        val m = monitor(clock = { 0 }) { listOf("a" to Job.State.RUNNING, "b" to Job.State.RUNNING) }

        assertThat(m.evaluate()).isEqualTo(ServingStatus.SERVING)
    }

    @Test
    fun `not serving when any job failed`() {
        val m = monitor(clock = { 0 }) { listOf("a" to Job.State.RUNNING, "b" to Job.State.FAILED) }

        assertThat(m.evaluate()).isEqualTo(ServingStatus.NOT_SERVING)
    }

    @Test
    fun `tolerates a non-running job within the grace window`() {
        var now = 0L
        val m = monitor(grace = 5.minutes, clock = { now }) { listOf("a" to Job.State.STARTING) }

        assertThat(m.evaluate()).isEqualTo(ServingStatus.SERVING)
        now = 4.minutes.inWholeMilliseconds
        assertThat(m.evaluate()).isEqualTo(ServingStatus.SERVING)
    }

    @Test
    fun `not serving when a job is stuck non-running past the grace window`() {
        var now = 0L
        // UNKNOWN is what a stuck restoring/rebalancing job maps to (the incident wedge).
        val m = monitor(grace = 5.minutes, clock = { now }) { listOf("a" to Job.State.UNKNOWN) }

        assertThat(m.evaluate()).isEqualTo(ServingStatus.SERVING)
        now = 6.minutes.inWholeMilliseconds
        assertThat(m.evaluate()).isEqualTo(ServingStatus.NOT_SERVING)
    }

    @Test
    fun `recovers to serving when a stuck job starts running again`() {
        var now = 0L
        var state = Job.State.RUNNING
        val m = monitor(grace = 5.minutes, clock = { now }) { listOf("a" to state) }

        m.evaluate() // running at t=0 establishes the baseline
        now = 3.minutes.inWholeMilliseconds
        state = Job.State.UNKNOWN
        assertThat(m.evaluate()).isEqualTo(ServingStatus.SERVING) // within grace
        now = 10.minutes.inWholeMilliseconds
        state = Job.State.RUNNING
        assertThat(m.evaluate()).isEqualTo(ServingStatus.SERVING) // running again
    }

    @Test
    fun `keeps running when reading job state throws`(): Unit = runBlocking {
        var explode = true
        // Models a concurrent-modification race in scheduler.ps(): without the guard in run(), this
        // exception would escape the launched coroutine and cancel the whole server's root scope.
        val m = HealthMonitor(
            jobStates = {
                if (explode) throw ConcurrentModificationException("ps() race") else listOf("a" to Job.State.RUNNING)
            },
            healthManager = HealthStatusManager(),
            interval = 5.milliseconds,
            clock = { 0 },
        )

        val job = launch { m.run() }
        delay(40) // several ticks while jobStates() throws

        assertThat(job.isActive).isTrue() // a throwing read did not crash the monitor

        explode = false
        job.cancelAndJoin()
    }
}
