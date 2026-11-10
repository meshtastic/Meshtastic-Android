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

class ParticulateAxisRangeTest {

    @Test
    fun axisTopIsTheDataMaxAndBottomSitsJustBelowZero() {
        val (min, max) = particulateAxisRange(dataMax = 200.0)

        assertEquals(200.0, max, 1e-9)
        assertEquals(-10.0, min, 1e-9)
    }

    @Test
    fun aZeroReadingIsNotOnTheBottomEdge() {
        val (min, max) = particulateAxisRange(dataMax = 0.0)

        assertTrue(min < 0.0, "zero must sit above the bottom edge")
        assertTrue(max > 0.0)
    }

    @Test
    fun smallValuesGetAtLeastOneUnitOfOffset() {
        assertEquals(-1.0, particulateAxisRange(dataMax = 8.0).first, 1e-9)
    }

    @Test
    fun co2IsOnItsOwnAxis() {
        val onEndAxis =
            AirQuality.entries.filter {
                it.axisPosition == com.patrykandpatrick.vico.compose.cartesian.axis.Axis.Position.Vertical.End
            }

        assertEquals(listOf(AirQuality.CO2), onEndAxis)
    }

    @Test
    fun theStartAxisCarriesAUnitOnlyWhenItsSeriesShareOne() {
        assertEquals("µg/m³", startAxisUnit(listOf(AirQuality.PM1_0, AirQuality.PM2_5, AirQuality.CO2)))
        assertEquals("", startAxisUnit(listOf(AirQuality.PM2_5, AirQuality.AQI)))
        assertEquals("", startAxisUnit(listOf(AirQuality.AQI)))
        assertEquals("", startAxisUnit(listOf(AirQuality.CO2)))
    }

    @Test
    fun theOffsetTickLabelIsNeverBlank() {
        val (min, _) = particulateAxisRange(dataMax = 40.0)

        assertEquals("0 µg/m³", particulateAxisLabel(min, "µg/m³"))
        assertEquals("0", particulateAxisLabel(min, ""))
        assertEquals("30 µg/m³", particulateAxisLabel(30.0, "µg/m³"))
    }
}
