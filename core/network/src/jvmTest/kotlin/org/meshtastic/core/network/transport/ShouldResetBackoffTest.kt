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
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ShouldResetBackoffTest {

    private val threshold = 30_000L

    @Test
    fun `no data never resets backoff`() {
        assertFalse(shouldResetBackoff(hadData = false, sessionUptimeMs = 0, thresholdMs = threshold))
        assertFalse(shouldResetBackoff(hadData = false, sessionUptimeMs = 60_000, thresholdMs = threshold))
    }

    @Test
    fun `data below threshold does not reset backoff`() {
        assertFalse(shouldResetBackoff(hadData = true, sessionUptimeMs = 0, thresholdMs = threshold))
        assertFalse(shouldResetBackoff(hadData = true, sessionUptimeMs = 1_000, thresholdMs = threshold))
        assertFalse(shouldResetBackoff(hadData = true, sessionUptimeMs = 29_999, thresholdMs = threshold))
    }

    @Test
    fun `data at exactly threshold resets backoff`() {
        assertTrue(shouldResetBackoff(hadData = true, sessionUptimeMs = 30_000, thresholdMs = threshold))
    }

    @Test
    fun `data above threshold resets backoff`() {
        assertTrue(shouldResetBackoff(hadData = true, sessionUptimeMs = 30_001, thresholdMs = threshold))
        assertTrue(shouldResetBackoff(hadData = true, sessionUptimeMs = 600_000, thresholdMs = threshold))
    }
}
