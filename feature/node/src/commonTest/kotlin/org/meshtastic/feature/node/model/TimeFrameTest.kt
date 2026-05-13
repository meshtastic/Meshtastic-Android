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
package org.meshtastic.feature.node.model

import org.meshtastic.core.common.util.nowSeconds
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@Suppress("MagicNumber")
class TimeFrameTest {

    // ---- timeThreshold ----

    @Test
    fun allTime_thresholdIsZero() {
        assertEquals(0L, TimeFrame.ALL_TIME.timeThreshold(now = 1000000L))
    }

    @Test
    fun oneHour_thresholdIsNowMinus3600() {
        val now = 1000000L
        assertEquals(now - 3600, TimeFrame.ONE_HOUR.timeThreshold(now = now))
    }

    @Test
    fun twentyFourHours_thresholdIsNowMinus86400() {
        val now = 1000000L
        assertEquals(now - 86400, TimeFrame.TWENTY_FOUR_HOURS.timeThreshold(now = now))
    }

    @Test
    fun sevenDays_thresholdIsNowMinus604800() {
        val now = 1000000L
        assertEquals(now - 604800, TimeFrame.SEVEN_DAYS.timeThreshold(now = now))
    }

    @Test
    fun twoWeeks_thresholdIsCorrect() {
        val now = 2000000L
        assertEquals(now - 1209600, TimeFrame.TWO_WEEKS.timeThreshold(now = now))
    }

    @Test
    fun oneMonth_thresholdIsCorrect() {
        val now = 3000000L
        assertEquals(now - 2592000, TimeFrame.ONE_MONTH.timeThreshold(now = now))
    }

    // ---- isAvailable ----

    @Test
    fun allTime_alwaysAvailable() {
        assertTrue(TimeFrame.ALL_TIME.isAvailable(oldestTimestampSeconds = nowSeconds, now = nowSeconds))
    }

    @Test
    fun oneHour_alwaysAvailable() {
        assertTrue(TimeFrame.ONE_HOUR.isAvailable(oldestTimestampSeconds = nowSeconds, now = nowSeconds))
    }

    @Test
    fun twentyFourHours_availableWhenDataOlderThan24h() {
        val now = 1000000L
        val oldest = now - 90000 // 25 hours ago
        assertTrue(TimeFrame.TWENTY_FOUR_HOURS.isAvailable(oldestTimestampSeconds = oldest, now = now))
    }

    @Test
    fun twentyFourHours_notAvailableWhenDataYoungerThan24h() {
        val now = 1000000L
        val oldest = now - 3600 // 1 hour ago
        assertFalse(TimeFrame.TWENTY_FOUR_HOURS.isAvailable(oldestTimestampSeconds = oldest, now = now))
    }

    @Test
    fun sevenDays_notAvailableForTwoDayOldData() {
        val now = 1000000L
        val oldest = now - (2 * 86400) // 2 days ago
        assertFalse(TimeFrame.SEVEN_DAYS.isAvailable(oldestTimestampSeconds = oldest, now = now))
    }

    @Test
    fun sevenDays_availableForEightDayOldData() {
        val now = 1000000L
        val oldest = now - (8 * 86400) // 8 days ago
        assertTrue(TimeFrame.SEVEN_DAYS.isAvailable(oldestTimestampSeconds = oldest, now = now))
    }

    @Test
    fun isAvailable_exactBoundary_returnsTrue() {
        val now = 1000000L
        // Exactly 24 hours of data range
        val oldest = now - 86400
        assertTrue(TimeFrame.TWENTY_FOUR_HOURS.isAvailable(oldestTimestampSeconds = oldest, now = now))
    }

    @Test
    fun isAvailable_justUnderBoundary_returnsFalse() {
        val now = 1000000L
        // One second less than 24 hours
        val oldest = now - 86399
        assertFalse(TimeFrame.TWENTY_FOUR_HOURS.isAvailable(oldestTimestampSeconds = oldest, now = now))
    }
}
