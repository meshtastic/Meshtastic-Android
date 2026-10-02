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
package org.meshtastic.feature.settings.util

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FixedOutputDurationsTest {

    @Test
    fun aDurationLabelledInSecondsIsWorthAThousandMilliseconds() {
        assertEquals(1000L, FixedOutputDurations.ONE_SECOND.value)
        assertEquals(5000L, FixedOutputDurations.FIVE_SECONDS.value)
        assertEquals(10_000L, FixedOutputDurations.TEN_SECONDS.value)
    }

    @Test
    fun theDurationsFirmwareDefaultsToAreOffered() {
        val offered = FixedOutputDurations.allowed.map { it.value }

        // NodeDB.cpp seeds output_ms at 100, 500 or 1000 depending on the board, so a radio nobody has configured
        // still matches a row here rather than reading as unset.
        assertTrue(offered.containsAll(listOf(100L, 500L, 1000L)), "firmware defaults missing from $offered")
    }

    @Test
    fun noDurationIsShorterThanFirmwareWouldDrive() {
        val shortest = FixedOutputDurations.allowed.map { it.value }.filter { it > 0L }.min()

        assertEquals(100L, shortest)
    }
}
