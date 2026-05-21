/*
 * Copyright (c) 2026 Meshtastic LLC
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package org.meshtastic.core.data.ai

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class RateLimiterTest {

    @Test
    fun permits_calls_under_limit() = runTest {
        val clock = FakeClock(Instant.fromEpochSeconds(1000))
        val rateLimiter = RateLimiter(clock)

        repeat(RateLimiter.MAX_CALLS) { assertIs<RateLimitResult.Permitted>(rateLimiter.tryAcquire()) }
    }

    @Test
    fun rejects_calls_over_limit() = runTest {
        val clock = FakeClock(Instant.fromEpochSeconds(1000))
        val rateLimiter = RateLimiter(clock)

        // Exhaust the limit
        repeat(RateLimiter.MAX_CALLS) { rateLimiter.tryAcquire() }

        val result = rateLimiter.tryAcquire()
        assertIs<RateLimitResult.Limited>(result)
        assertEquals(61, result.retryAfterSeconds) // full window remaining + 1
    }

    @Test
    fun permits_after_window_expires() = runTest {
        val clock = FakeClock(Instant.fromEpochSeconds(1000))
        val rateLimiter = RateLimiter(clock)

        // Exhaust the limit
        repeat(RateLimiter.MAX_CALLS) { rateLimiter.tryAcquire() }

        // Advance past the window
        clock.currentTime = Instant.fromEpochSeconds(1000) + RateLimiter.WINDOW_DURATION + 1.seconds

        assertIs<RateLimitResult.Permitted>(rateLimiter.tryAcquire())
    }

    @Test
    fun sliding_window_evicts_oldest_entry() = runTest {
        val clock = FakeClock(Instant.fromEpochSeconds(1000))
        val rateLimiter = RateLimiter(clock)

        // Fill the window with calls 10 seconds apart
        repeat(RateLimiter.MAX_CALLS) { i ->
            clock.currentTime = Instant.fromEpochSeconds(1000L + i * 10)
            rateLimiter.tryAcquire()
        }

        // At t=1050, first call (t=1000) is still in window (threshold is t=990)
        clock.currentTime = Instant.fromEpochSeconds(1050)
        assertIs<RateLimitResult.Limited>(rateLimiter.tryAcquire())

        // At t=1061 — first call (t=1000) should have expired from window
        clock.currentTime = Instant.fromEpochSeconds(1061)
        assertIs<RateLimitResult.Permitted>(rateLimiter.tryAcquire())
    }

    @Test
    fun retry_after_is_accurate() = runTest {
        val clock = FakeClock(Instant.fromEpochSeconds(1000))
        val rateLimiter = RateLimiter(clock)

        // All calls at t=1000
        repeat(RateLimiter.MAX_CALLS) { rateLimiter.tryAcquire() }

        // Check at t=1030 (halfway through window)
        clock.currentTime = Instant.fromEpochSeconds(1030)
        val result = rateLimiter.tryAcquire()
        assertIs<RateLimitResult.Limited>(result)
        // Oldest at t=1000, expires at t=1060, now is t=1030, so retryAfter = 31
        assertEquals(31, result.retryAfterSeconds)
    }
}

/** Simple fake Clock for testing. */
private class FakeClock(var currentTime: Instant) : Clock {
    override fun now(): Instant = currentTime
}
