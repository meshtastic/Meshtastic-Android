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
package org.meshtastic.feature.map

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MapTimeWindowsTest {

    @Test
    fun `the any filter keeps every point however old`() {
        assertTrue(LastHeardFilter.Any.includes(recordedAtSeconds = 0, nowSeconds = 1_000_000))
    }

    @Test
    fun `a point inside the window is kept and one outside is dropped`() {
        val now = 10_000L
        assertTrue(LastHeardFilter.OneHour.includes(recordedAtSeconds = 9_000, nowSeconds = now))
        assertFalse(LastHeardFilter.OneHour.includes(recordedAtSeconds = 100, nowSeconds = now))
    }

    @Test
    fun `a node heard within the pulse window pulses`() {
        assertTrue(heardJustNow(lastHeard = 998, nowSeconds = 1_000))
        assertFalse(heardJustNow(lastHeard = 900, nowSeconds = 1_000))
    }
}
