package io.typestream.connectors.webvisits

import java.time.Instant
import java.time.ZoneOffset
import kotlin.math.ln
import kotlin.math.sin
import kotlin.random.Random

/**
 * Simulates realistic web traffic patterns with:
 * - Time-of-day patterns (peak hours)
 * - Random bursts
 * - Slow periods
 */
class TrafficSimulator(
    private val baseRatePerSecond: Double = 10.0,
    private val burstMultiplier: Double = 5.0,
    private val burstProbability: Double = 0.02
) {
    private val random = Random.Default
    private var inBurst = false
    private var burstRemaining = 0

    /**
     * Calculate delay until next event in milliseconds.
     * Uses exponential distribution for realistic inter-arrival times.
     */
    fun nextDelayMs(): Long {
        val adjustedRate = calculateCurrentRate()

        // Exponential distribution for inter-arrival times
        val lambda = adjustedRate / 1000.0 // Convert to events per ms
        val u = random.nextDouble()
        val delay = (-ln(1.0 - u) / lambda).toLong()

        return delay.coerceIn(1, 5000) // Between 1ms and 5s
    }

    private fun calculateCurrentRate(): Double {
        var rate = baseRatePerSecond

        // Apply time-of-day modifier
        rate *= timeOfDayMultiplier()

        // Handle burst mode
        if (inBurst) {
            rate *= burstMultiplier
            burstRemaining--
            if (burstRemaining <= 0) {
                inBurst = false
            }
        } else if (random.nextDouble() < burstProbability) {
            inBurst = true
            burstRemaining = random.nextInt(10, 50) // Burst lasts 10-50 events
        }

        return rate
    }

    /**
     * Simulates traffic patterns based on time of day (UTC).
     * Peak: 14:00-22:00 UTC (covers US business hours)
     * Off-peak: 02:00-10:00 UTC
     */
    private fun timeOfDayMultiplier(): Double {
        val hour = Instant.now().atOffset(ZoneOffset.UTC).hour

        // Sine wave pattern: peak at 18:00 UTC, trough at 06:00 UTC
        val radians = ((hour - 6) * Math.PI / 12)
        val modifier = 0.5 + 0.5 * sin(radians)

        return 0.3 + (modifier * 0.7) // Range: 0.3x to 1.0x of base rate
    }
}
