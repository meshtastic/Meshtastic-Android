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
package org.meshtastic.core.common.util

import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RelativeDayTest {

    private val utc = TimeZone.UTC

    // 2026-08-24T00:00:00Z
    private val dayStart = 1_787_529_600_000L
    private val oneHour = 3_600_000L
    private val oneDay = 24 * oneHour

    @Test
    fun `same calendar day is same day even when nearly a day apart`() {
        assertTrue(isSameLocalDay(dayStart + oneHour, dayStart + 23 * oneHour, utc))
    }

    @Test
    fun `midnight starts a new day even minutes apart`() {
        // The point of comparing dates rather than elapsed millis: 23:59 and 00:01 are two minutes apart, on two
        // different dates.
        assertFalse(isSameLocalDay(dayStart - 60_000L, dayStart + 60_000L, utc))
    }

    @Test
    fun `classifies today yesterday and older`() {
        val now = dayStart + 12 * oneHour
        assertEquals(RelativeDay.Today, relativeDayOf(dayStart + oneHour, now, utc))
        assertEquals(RelativeDay.Yesterday, relativeDayOf(dayStart - oneHour, now, utc))
        assertEquals(RelativeDay.Older, relativeDayOf(dayStart - 2 * oneDay, now, utc))
    }
}
