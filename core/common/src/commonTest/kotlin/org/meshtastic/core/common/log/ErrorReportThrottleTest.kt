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
package org.meshtastic.core.common.log

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class ErrorReportThrottleTest {

    private var now = 0L

    private fun throttle(limit: Int = 3, windowMs: Long = 1_000L, maxKeys: Int = 8) =
        ErrorReportThrottle(limit = limit, windowMs = windowMs, maxKeys = maxKeys, nowMs = { now })

    @Test
    fun `reports up to the limit then suppresses`() {
        val throttle = throttle()

        repeat(3) { assertEquals(0, throttle.acquire("a")) }
        assertNull(throttle.acquire("a"))
        assertNull(throttle.acquire("a"))
    }

    @Test
    fun `the first report after a window carries the suppressed count`() {
        val throttle = throttle()

        repeat(3) { throttle.acquire("a") }
        repeat(7) { throttle.acquire("a") }

        now += 1_000L
        assertEquals(7, throttle.acquire("a"))
        assertEquals(0, throttle.acquire("a"), "the count resets once it has been handed over")
    }

    @Test
    fun `signatures are throttled independently`() {
        val throttle = throttle()

        repeat(3) { throttle.acquire("a") }
        assertNull(throttle.acquire("a"))

        assertEquals(0, throttle.acquire("b"), "one flooding signature must not silence another")
    }

    @Test
    fun `a signature reported below the limit is never suppressed`() {
        val throttle = throttle()

        repeat(20) {
            now += 1_000L
            assertNotNull(throttle.acquire("a"))
        }
    }

    @Test
    fun `the key set stays bounded`() {
        val throttle = throttle(maxKeys = 8)

        repeat(100) { throttle.acquire("key-$it") }

        // Eviction must not start suppressing a brand-new signature.
        assertEquals(0, throttle.acquire("fresh"))
    }

    @Test
    fun `signature collapses a variable tail beyond the prefix`() {
        // Longer than the 64 characters the signature keeps, so only the tail differs.
        val head = "MQTT connect failed unexpectedly, retrying in 30000ms: noLocal subscription option unsupported"
        val a = ErrorReportThrottle.signature("tag", "$head (attempt 1)")
        val b = ErrorReportThrottle.signature("tag", "$head (attempt 97)")

        assertEquals(a, b, "the same call site must collapse to one signature")
    }

    @Test
    fun `signature separates messages that differ within the prefix`() {
        assertNotEquals(
            ErrorReportThrottle.signature("tag", "Bluetooth disabled"),
            ErrorReportThrottle.signature("tag", "Location disabled"),
        )
    }

    @Test
    fun `signature separates distinct tags`() {
        assertNotEquals(ErrorReportThrottle.signature("a", "m"), ErrorReportThrottle.signature("b", "m"))
    }
}
