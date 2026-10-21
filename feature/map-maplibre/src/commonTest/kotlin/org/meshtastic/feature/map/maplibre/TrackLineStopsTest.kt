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
package org.meshtastic.feature.map.maplibre

import org.maplibre.spatialk.geojson.Position
import org.meshtastic.feature.map.OLDEST_TRACK_ALPHA
import org.meshtastic.feature.map.trackFadeAlpha
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TrackLineStopsTest {

    @Test
    fun `each stop sits at its vertex share of the length with that fix alpha`() {
        val points = listOf(Position(0.0, 0.0), Position(0.0, 0.01), Position(0.0, 0.04))
        val stops = trackLineStops(points, listOf(0f, 0.5f, 1f))
        assertEquals(listOf(0.0, 0.25, 1.0), stops.map { it.first }, absoluteTolerance = 1e-6)
        assertEquals(listOf(OLDEST_TRACK_ALPHA, trackFadeAlpha(0.5f), 1f), stops.map { it.second })
    }

    @Test
    fun `a vertex adding no length replaces the stop before it so inputs strictly ascend`() {
        val points = listOf(Position(0.0, 0.0), Position(0.0, 0.01), Position(0.0, 0.01), Position(0.0, 0.02))
        val stops = trackLineStops(points, listOf(0f, 0.2f, 0.6f, 1f))
        assertEquals(3, stops.size)
        assertTrue(stops.zipWithNext().all { (a, b) -> b.first > a.first })
        assertEquals(trackFadeAlpha(0.6f), stops[1].second)
    }

    private fun assertEquals(expected: List<Double>, actual: List<Double>, absoluteTolerance: Double) {
        assertEquals(expected.size, actual.size)
        expected.zip(actual).forEach { (e, a) -> assertEquals(e, a, absoluteTolerance) }
    }
}
