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
package org.meshtastic.feature.map.terrain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ContourIntervalsTest {

    @Test
    fun `z13-14 metric band matches iOS exactly`() {
        assertEquals(ContourIntervalBand(50f, 250f), ContourIntervals.forZoom(13, metric = true))
        assertEquals(ContourIntervalBand(50f, 250f), ContourIntervals.forZoom(14, metric = true))
    }

    @Test
    fun `z13-14 imperial band matches iOS's 200ft-1000ft - converted to meters`() {
        val band = ContourIntervals.forZoom(13, metric = false)
        assertEquals(200f * 0.3048f, band.minorMeters, absoluteTolerance = 1e-4f)
        assertEquals(1000f * 0.3048f, band.indexMeters, absoluteTolerance = 1e-4f)
    }

    @Test
    fun `every band's index interval is exactly 5x its minor interval`() {
        for (zoom in listOf(5, 10, 11, 12, 13, 14, 15, 20)) {
            for (metric in listOf(true, false)) {
                val band = ContourIntervals.forZoom(zoom, metric)
                assertEquals(
                    band.minorMeters * 5f,
                    band.indexMeters,
                    absoluteTolerance = 1e-3f,
                    "zoom=$zoom metric=$metric",
                )
            }
        }
    }

    @Test
    fun `zoom bands step down at exactly 11 - 13 - and 15`() {
        assertEquals(500f, ContourIntervals.forZoom(10, metric = true).minorMeters)
        assertEquals(100f, ContourIntervals.forZoom(11, metric = true).minorMeters)
        assertEquals(100f, ContourIntervals.forZoom(12, metric = true).minorMeters)
        assertEquals(50f, ContourIntervals.forZoom(13, metric = true).minorMeters)
        assertEquals(20f, ContourIntervals.forZoom(15, metric = true).minorMeters)
        assertEquals(20f, ContourIntervals.forZoom(20, metric = true).minorMeters)
    }

    @Test
    fun `levelsForZoom generates every minor multiple up to the elevation ceiling`() {
        // z15 metric minor = 20m; up to 100m should yield exactly 20,40,60,80,100.
        val levels = ContourIntervals.levelsForZoom(zoom = 15, metric = true, maxElevationMeters = 100f)
        assertEquals(listOf(20f, 40f, 60f, 80f, 100f), levels)
    }

    @Test
    fun `isIndexLevel is true only on multiples of the index interval`() {
        // z15 metric: minor 20, index 100 -> 100 and 200 are index levels, 20/40/etc are not.
        assertTrue(ContourIntervals.isIndexLevel(100f, zoom = 15, metric = true))
        assertTrue(ContourIntervals.isIndexLevel(200f, zoom = 15, metric = true))
        assertFalse(ContourIntervals.isIndexLevel(20f, zoom = 15, metric = true))
        assertFalse(ContourIntervals.isIndexLevel(80f, zoom = 15, metric = true))
    }
}
