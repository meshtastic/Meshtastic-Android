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
package org.meshtastic.feature.map.mapcompose

import org.meshtastic.feature.map.mapcompose.geo.GeoPoint
import org.meshtastic.feature.map.mapcompose.geo.PolylineGeometry
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

@Suppress("MagicNumber")
class PolylineGeometryTest {

    @Test
    fun offsetPolyline_tooFewPoints_returnsInput() {
        val single = listOf(GeoPoint(1.0, 1.0))
        assertEquals(single, PolylineGeometry.offsetPolyline(single, 100.0))
    }

    @Test
    fun offsetPolyline_zeroOffset_returnsInput() {
        val line = listOf(GeoPoint(0.0, 0.0), GeoPoint(1.0, 0.0))
        assertEquals(line, PolylineGeometry.offsetPolyline(line, 0.0))
    }

    @Test
    fun offsetPolyline_northboundLine_shiftsEast() {
        val line = listOf(GeoPoint(0.0, 0.0), GeoPoint(1.0, 0.0))
        val offset = PolylineGeometry.offsetPolyline(line, 1000.0)
        // Heading is north (0°); +90° perpendicular means every point moves east (longitude grows).
        offset.forEachIndexed { i, p ->
            assertTrue(p.longitude > line[i].longitude, "point $i did not move east")
            assertTrue(abs(p.latitude - line[i].latitude) < 0.01, "point $i latitude drifted")
        }
    }

    @Test
    fun offsetPolyline_negativeSideMultiplier_shiftsWest() {
        val line = listOf(GeoPoint(0.0, 0.0), GeoPoint(1.0, 0.0))
        val offset = PolylineGeometry.offsetPolyline(line, 1000.0, sideMultiplier = -1.0)
        offset.forEachIndexed { i, p -> assertTrue(p.longitude < line[i].longitude, "point $i did not move west") }
    }

    @Test
    fun offsetPolyline_forwardAndReturn_mirrorAroundOriginal() {
        val line = listOf(GeoPoint(10.0, 10.0), GeoPoint(10.5, 10.5), GeoPoint(11.0, 11.0))
        val forward = PolylineGeometry.offsetPolyline(line, 500.0, sideMultiplier = 1.0)
        val back = PolylineGeometry.offsetPolyline(line, 500.0, sideMultiplier = -1.0)
        line.indices.forEach { i ->
            val midLat = (forward[i].latitude + back[i].latitude) / 2
            val midLon = (forward[i].longitude + back[i].longitude) / 2
            assertTrue(abs(midLat - line[i].latitude) < 1e-4)
            assertTrue(abs(midLon - line[i].longitude) < 1e-4)
        }
    }

    @Test
    fun circlePolygon_isClosedWithRequestedResolution() {
        val ring = PolylineGeometry.circlePolygon(45.0, 7.0, 200.0, segments = 32)
        assertEquals(33, ring.size)
        assertEquals(ring.first(), ring.last())
    }

    @Test
    fun circlePolygon_pointsLieAtRequestedRadius() {
        val ring = PolylineGeometry.circlePolygon(0.0, 0.0, 1000.0, segments = 16)
        // 1000 m ≈ 0.008983 degrees at the equator; every vertex should be that far from the center.
        val expectedDegrees = 1000.0 / 111_319.5
        ring.dropLast(1).forEach { p ->
            val dist = kotlin.math.sqrt(p.latitude * p.latitude + p.longitude * p.longitude)
            assertTrue(abs(dist - expectedDegrees) < expectedDegrees * 0.02, "vertex off radius: $p")
        }
    }

    @Test
    fun circlePolygon_rejectsDegenerateSegmentCount() {
        assertFailsWith<IllegalArgumentException> { PolylineGeometry.circlePolygon(0.0, 0.0, 100.0, segments = 2) }
    }
}
