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
package org.meshtastic.feature.node.metrics

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PressureAxisRangeTest {

    @Test
    fun nearSeaLevelUsesStandardWindow() {
        // Typical readings sit inside 950-1050, so the axis stays comparable across sessions/nodes.
        assertEquals(950.0 to 1050.0, pressureAxisRange(dataMin = 1005.0, dataMax = 1020.0))
    }

    @Test
    fun windowIsAlwaysTheSameWidthWhenNotOverflowing() {
        listOf(1005.0 to 1020.0, 835.0 to 848.0, 1042.0 to 1061.0).forEach { (min, max) ->
            val (lo, hi) = pressureAxisRange(min, max)
            assertEquals(100.0, hi - lo, 1e-9, "fixed window width for $min..$max")
        }
    }

    @Test
    fun highAltitudeWindowSlidesDownToContainReadings() {
        // Station pressure at altitude (~1500 m) reads well below 950; window slides down instead of clipping.
        val (lo, hi) = pressureAxisRange(dataMin = 835.0, dataMax = 848.0)
        assertEquals(835.0 to 935.0, lo to hi)
        assertTrue(835.0 >= lo && 848.0 <= hi, "readings fit inside the window")
    }

    @Test
    fun highPressureWindowSlidesUpToContainReadings() {
        val (lo, hi) = pressureAxisRange(dataMin = 1042.0, dataMax = 1061.0)
        assertEquals(961.0 to 1061.0, lo to hi)
    }

    @Test
    fun spanWiderThanWindowFallsBackToExactRange() {
        // A node that moved thousands of metres can span more than the fixed width; show it all rather than clip.
        assertEquals(830.0 to 1015.0, pressureAxisRange(dataMin = 830.0, dataMax = 1015.0))
    }

    @Test
    fun singleReadingStillGetsTheStandardWindow() {
        assertEquals(950.0 to 1050.0, pressureAxisRange(dataMin = 1013.0, dataMax = 1013.0))
    }
}
