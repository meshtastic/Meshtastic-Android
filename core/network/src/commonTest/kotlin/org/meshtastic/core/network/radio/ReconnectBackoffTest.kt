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
package org.meshtastic.core.network.radio

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/**
 * Tests the exponential backoff schedule used by [BleRadioTransport] when consecutive connection attempts fail. The
 * schedule is: failure #1 → 5 s failure #2 → 10 s failure #3 → 20 s failure #4 → 40 s failure #5+ → 60 s (capped)
 */
class ReconnectBackoffTest {

    @Test
    fun `zero failures yields base delay`() {
        assertEquals(5.seconds, computeReconnectBackoff(0))
    }

    @Test
    fun `first failure yields 5s`() {
        assertEquals(5.seconds, computeReconnectBackoff(1))
    }

    @Test
    fun `second failure yields 10s`() {
        assertEquals(10.seconds, computeReconnectBackoff(2))
    }

    @Test
    fun `third failure yields 20s`() {
        assertEquals(20.seconds, computeReconnectBackoff(3))
    }

    @Test
    fun `fourth failure yields 40s`() {
        assertEquals(40.seconds, computeReconnectBackoff(4))
    }

    @Test
    fun `fifth failure is capped at 60s`() {
        assertEquals(60.seconds, computeReconnectBackoff(5))
    }

    @Test
    fun `large failure count stays capped at 60s`() {
        assertEquals(60.seconds, computeReconnectBackoff(100))
    }

    @Test
    fun `backoff is strictly increasing up to the cap`() {
        val values = (1..5).map { computeReconnectBackoff(it) }
        for (i in 0 until values.size - 1) {
            assertTrue(
                values[i] < values[i + 1],
                "Expected backoff[${i + 1}] (${values[i]}) < backoff[${i + 2}] (${values[i + 1]})",
            )
        }
    }
}
