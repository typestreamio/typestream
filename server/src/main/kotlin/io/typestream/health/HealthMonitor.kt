package io.typestream.health

import io.github.oshai.kotlinlogging.KotlinLogging
import io.grpc.health.v1.HealthCheckResponse.ServingStatus
import io.grpc.services.HealthStatusManager
import io.typestream.scheduler.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Periodically derives an overall gRPC serving status from the scheduler's managed jobs and pushes
 * it to [healthManager], so a Kubernetes liveness probe can restart a pod whose pipelines have
 * wedged or failed.
 *
 * The predicate is intentionally state-based (see [evaluate]): a job that is `FAILED`, or that has
 * been present-but-not-`RUNNING` for longer than [graceWindow], makes the whole server report
 * `NOT_SERVING`. The grace window absorbs normal startup and rebalancing (both surface as a
 * non-`RUNNING` state) without false positives. It deliberately does not cover a thread that hangs
 * while still reporting `RUNNING` — that needs a lag/progress check (see the plan's Deferred notes).
 *
 * @param jobStates the live `(id, state)` of every managed job, e.g. `{ scheduler.ps().map { it.id to it.state() } }`
 */
class HealthMonitor(
    private val jobStates: () -> List<Pair<String, Job.State>>,
    private val healthManager: HealthStatusManager,
    private val graceWindow: Duration = 15.minutes,
    private val interval: Duration = 15.seconds,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val logger = KotlinLogging.logger {}

    // Per job id, the last time we observed it RUNNING (or first saw it). Used to grant a grace
    // window before a persistently non-RUNNING job is treated as unhealthy.
    private val lastHealthyAt = mutableMapOf<String, Long>()

    suspend fun run() = coroutineScope {
        while (isActive) {
            try {
                val status = evaluate()
                healthManager.setStatus(HealthStatusManager.SERVICE_NAME_ALL_SERVICES, status)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // A transient failure to read job state (e.g. a concurrent-modification race in
                // scheduler.ps()) must not crash the monitor — its launch is a child of the server's
                // root scope, so an escaping exception would cancel the whole gRPC server. Leave the
                // last reported status in place and re-evaluate next tick.
                logger.warn(e) { "health evaluation failed; leaving serving status unchanged" }
            }
            delay(interval)
        }
    }

    internal fun evaluate(): ServingStatus {
        val now = clock()
        val states = jobStates()

        // Forget jobs that are no longer managed so the map doesn't grow unbounded.
        lastHealthyAt.keys.retainAll(states.map { it.first }.toSet())

        var healthy = true
        for ((id, state) in states) {
            when (state) {
                Job.State.RUNNING -> lastHealthyAt[id] = now
                Job.State.FAILED -> {
                    logger.warn { "job $id is FAILED; reporting NOT_SERVING" }
                    healthy = false
                }
                else -> {
                    val since = lastHealthyAt.getOrPut(id) { now }
                    if (now - since > graceWindow.inWholeMilliseconds) {
                        logger.warn { "job $id has been $state past the grace window; reporting NOT_SERVING" }
                        healthy = false
                    }
                }
            }
        }

        return if (healthy) ServingStatus.SERVING else ServingStatus.NOT_SERVING
    }
}
