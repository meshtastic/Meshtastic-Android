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

class ChartGapsTest {
    private fun split(vararg times: Int) = splitAtGaps(times.toList()) { it }

    @Test
    fun emptyAndSingleInputsYieldNoGapSplit() {
        assertEquals(emptyList(), split())
        assertEquals(listOf(listOf(100)), split(100))
    }

    @Test
    fun evenlySpacedReadingsStayOneRun() {
        assertEquals(listOf(listOf(0, 900, 1800, 2700)), split(0, 900, 1800, 2700))
    }

    @Test
    fun longSilenceBreaksTheRun() {
        assertEquals(
            listOf(listOf(0, 900, 1800), listOf(30_000, 30_900)),
            split(0, 900, 1800, 30_000, 30_900),
        )
    }

    @Test
    fun thresholdScalesWithMedianSpacing() {
        assertEquals(listOf(listOf(0, 1800, 3600, 9000)), split(0, 1800, 3600, 9000))
        assertEquals(listOf(listOf(0, 1800, 3600), listOf(9100)), split(0, 1800, 3600, 9100))
    }

    @Test
    fun evenSpacingCountUsesTheTrueMedian() {
        assertEquals(listOf(listOf(0, 60, 120, 720), listOf(2220)), split(0, 60, 120, 720, 2220))
    }

    @Test
    fun denseReadingsUseTheFiveMinuteFloor() {
        assertEquals(listOf(listOf(0, 30, 60, 360)), split(0, 30, 60, 360))
        assertEquals(listOf(listOf(0, 30, 60), listOf(361)), split(0, 30, 60, 361))
    }

    @Test
    fun isolatedReadingBetweenGapsIsItsOwnRun() {
        assertEquals(
            listOf(listOf(0, 60, 120), listOf(10_000), listOf(20_000, 20_060)),
            split(0, 60, 120, 10_000, 20_000, 20_060),
        )
    }
}
