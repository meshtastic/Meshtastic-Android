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
package org.meshtastic.core.repository

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours

class MeshLogRetentionTest {

    @Test
    fun `one hour sentinel resolves to an hour and not a negative day count`() {
        assertEquals(1.hours, MeshLogRetention.windowOrNull(MeshLogRetention.ONE_HOUR))
    }

    @Test
    fun `keep forever sentinel resolves to no window`() {
        assertNull(MeshLogRetention.windowOrNull(MeshLogRetention.KEEP_FOREVER))
    }

    @Test
    fun `positive settings resolve to that many days`() {
        assertEquals(1.days, MeshLogRetention.windowOrNull(1))
        assertEquals(7.days, MeshLogRetention.windowOrNull(7))
        assertEquals(30.days, MeshLogRetention.windowOrNull(MeshLogPrefs.DEFAULT_RETENTION_DAYS))
        assertEquals(365.days, MeshLogRetention.windowOrNull(MeshLogPrefs.MAX_RETENTION_DAYS))
    }

    @Test
    fun `out of range negative settings still resolve to an hour`() {
        assertEquals(1.hours, MeshLogRetention.windowOrNull(-2))
        assertEquals(1.hours, MeshLogRetention.windowOrNull(Int.MIN_VALUE))
    }

    @Test
    fun `every window is positive so the cutoff never lands in the future`() {
        val settings = listOf(Int.MIN_VALUE, -2, MeshLogRetention.ONE_HOUR, 1, 7, 365, Int.MAX_VALUE)
        settings.forEach { setting ->
            val window = MeshLogRetention.windowOrNull(setting)
            assertTrue(window == null || window.isPositive(), "window for $setting was $window")
        }
    }

    @Test
    fun `the lowest selectable setting is the one hour sentinel`() {
        assertEquals(MeshLogRetention.ONE_HOUR, MeshLogPrefs.MIN_RETENTION_DAYS)
    }
}
