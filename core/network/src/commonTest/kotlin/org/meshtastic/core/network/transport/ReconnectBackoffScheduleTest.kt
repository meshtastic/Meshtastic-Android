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
package org.meshtastic.core.network.transport

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReconnectBackoffScheduleTest {

    @Test
    fun `the delay doubles from the minimum up to the cap`() {
        val backoff = ReconnectBackoff(minMillis = 1_000, maxMillis = 5_000, shortSessionThresholdMs = 30_000)
        assertEquals(listOf(1_000L, 2_000L, 4_000L, 5_000L, 5_000L), List(5) { backoff.nextDelayMillis() })
        assertEquals(6, backoff.attempt)
    }

    @Test
    fun `a long session with data resets the schedule`() {
        val backoff = ReconnectBackoff(minMillis = 1_000, maxMillis = 60_000, shortSessionThresholdMs = 30_000)
        repeat(3) { backoff.nextDelayMillis() }

        assertTrue(backoff.onSessionEnded(hadData = true, sessionUptimeMs = 30_000))
        assertEquals(1, backoff.attempt)
        assertEquals(1_000, backoff.nextDelayMillis())
    }

    @Test
    fun `a short or silent session keeps the delay growing`() {
        val backoff = ReconnectBackoff(minMillis = 1_000, maxMillis = 60_000, shortSessionThresholdMs = 30_000)
        backoff.nextDelayMillis()

        assertFalse(backoff.onSessionEnded(hadData = true, sessionUptimeMs = 29_999))
        assertFalse(backoff.onSessionEnded(hadData = false, sessionUptimeMs = 600_000))
        assertEquals(2_000, backoff.nextDelayMillis())
    }

    @Test
    fun `a floor lifts one delay and the schedule continues from it`() {
        val backoff = ReconnectBackoff(minMillis = 1_000, maxMillis = 300_000, shortSessionThresholdMs = 30_000)

        assertEquals(30_000, backoff.nextDelayMillis(floorMillis = 30_000))
        assertEquals(60_000, backoff.nextDelayMillis())
    }
}
