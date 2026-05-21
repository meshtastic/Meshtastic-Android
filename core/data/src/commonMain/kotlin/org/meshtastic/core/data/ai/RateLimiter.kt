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

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.koin.core.annotation.Single
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/**
 * Sliding-window rate limiter for AI-triggered operations.
 *
 * Tracks the last [maxCalls] invocation timestamps. A new call is permitted only if fewer than [maxCalls] occurred
 * within the [windowDuration]. This prevents AI agents from flooding the mesh network.
 */
@Single
class RateLimiter(private val clock: Clock) {

    private val mutex = Mutex()
    private val timestamps = ArrayDeque<Instant>(MAX_CALLS)

    /**
     * Attempt to acquire a permit for one invocation.
     *
     * @return [RateLimitResult.Permitted] if under the limit, or [RateLimitResult.Limited] with the number of seconds
     *   until a slot frees up.
     */
    suspend fun tryAcquire(): RateLimitResult = mutex.withLock {
        val now = clock.now()
        val windowStart = now - WINDOW_DURATION

        // Evict timestamps outside the window
        while (timestamps.isNotEmpty() && timestamps.first() <= windowStart) {
            timestamps.removeFirst()
        }

        return if (timestamps.size < MAX_CALLS) {
            timestamps.addLast(now)
            RateLimitResult.Permitted
        } else {
            val oldestInWindow = timestamps.first()
            val retryAfter = ((oldestInWindow + WINDOW_DURATION) - now).inWholeSeconds.toInt() + 1
            RateLimitResult.Limited(retryAfterSeconds = retryAfter.coerceAtLeast(1))
        }
    }

    companion object {
        const val MAX_CALLS = 5
        val WINDOW_DURATION = 60.seconds
    }
}

sealed class RateLimitResult {
    data object Permitted : RateLimitResult()

    data class Limited(val retryAfterSeconds: Int) : RateLimitResult()
}
