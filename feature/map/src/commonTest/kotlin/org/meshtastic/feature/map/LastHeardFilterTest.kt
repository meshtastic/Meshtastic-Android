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
import kotlin.test.assertEquals

@Suppress("MagicNumber")
class LastHeardFilterTest {

    @Test
    fun fromSeconds_knownValues() {
        assertEquals(LastHeardFilter.Any, LastHeardFilter.fromSeconds(0L))
        assertEquals(LastHeardFilter.OneHour, LastHeardFilter.fromSeconds(3600L))
        assertEquals(LastHeardFilter.EightHours, LastHeardFilter.fromSeconds(28800L))
        assertEquals(LastHeardFilter.OneDay, LastHeardFilter.fromSeconds(86400L))
        assertEquals(LastHeardFilter.TwoDays, LastHeardFilter.fromSeconds(172800L))
    }

    @Test
    fun fromSeconds_unknownValue_defaultsToAny() {
        assertEquals(LastHeardFilter.Any, LastHeardFilter.fromSeconds(9999L))
        assertEquals(LastHeardFilter.Any, LastHeardFilter.fromSeconds(-1L))
        assertEquals(LastHeardFilter.Any, LastHeardFilter.fromSeconds(Long.MAX_VALUE))
    }

    @Test
    fun seconds_matchExpectedValues() {
        assertEquals(0L, LastHeardFilter.Any.seconds)
        assertEquals(3600L, LastHeardFilter.OneHour.seconds)
        assertEquals(28800L, LastHeardFilter.EightHours.seconds)
        assertEquals(86400L, LastHeardFilter.OneDay.seconds)
        assertEquals(172800L, LastHeardFilter.TwoDays.seconds)
    }
}
