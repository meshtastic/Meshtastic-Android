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
package org.meshtastic.feature.map.component

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Clock

/**
 * The two sentinels the `expire` wire field carries instead of being nullable.
 *
 * The editor reads them in four places, and each used to spell the check out itself.
 */
class WaypointExpiryTest {

    @Test
    fun `neither sentinel counts as an expiry`() {
        assertFalse(0.isExpirySet(), "0 means no expiry was ever set")
        assertFalse(Int.MAX_VALUE.isExpirySet(), "Int.MAX_VALUE means never expires")
    }

    @Test
    fun `a real timestamp counts as an expiry`() {
        assertTrue(1_800_000_000.isExpirySet())
    }

    @Test
    fun `a set expiry is returned as itself`() {
        val seconds = 1_800_000_000
        assertEquals(seconds.toLong(), seconds.expiryInstantOrDefault().epochSeconds)
    }

    @Test
    fun `both sentinels fall back to the same default ahead of now`() {
        val now = Clock.System.now()
        listOf(0, Int.MAX_VALUE).forEach { sentinel ->
            val fallback = sentinel.expiryInstantOrDefault()
            assertTrue(fallback > now, "$sentinel should seed an expiry in the future, got $fallback")
        }
    }
}
