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

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TimeUtilsTest {
    @Test
    fun testNowMillis() {
        val start = nowMillis
        // Just verify it returns something sensible (not 0)
        assertTrue(start > 0)
    }

    @Test
    fun testNowSeconds() {
        val start = nowSeconds
        assertTrue(start > 0)
    }

    @Test
    fun clampTimestampToNow_pastTimestamp_unchanged() {
        val past = (nowSeconds - 3600).toInt()
        assertEquals(past, clampTimestampToNow(past))
    }

    @Test
    fun clampTimestampToNow_futureTimestamp_clampedToNow() {
        val future = (nowSeconds + 86400).toInt()
        val clamped = clampTimestampToNow(future)
        assertTrue(clamped <= nowSeconds.toInt())
    }

    @Test
    fun clampTimestampToNow_zero_unchanged() {
        assertEquals(0, clampTimestampToNow(0))
    }
}
