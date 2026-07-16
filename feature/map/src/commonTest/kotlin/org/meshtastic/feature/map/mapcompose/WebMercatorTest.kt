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

import org.meshtastic.feature.map.mapcompose.geo.NormalizedPoint
import org.meshtastic.feature.map.mapcompose.geo.WebMercator
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@Suppress("MagicNumber")
class WebMercatorTest {

    private fun assertClose(expected: Double, actual: Double, epsilon: Double = 1e-9) {
        assertTrue(abs(expected - actual) < epsilon, "expected $expected but was $actual")
    }

    @Test
    fun origin_mapsToCenter() {
        val p = WebMercator.toNormalized(0.0, 0.0)
        assertClose(0.5, p.x)
        assertClose(0.5, p.y)
    }

    @Test
    fun corners_mapToUnitSquare() {
        assertClose(0.0, WebMercator.longitudeToX(-180.0))
        assertClose(1.0, WebMercator.longitudeToX(180.0))
        // Top of the mercator square is the max representable latitude, bottom the min.
        assertClose(0.0, WebMercator.latitudeToY(WebMercator.MAX_LATITUDE), 1e-6)
        assertClose(1.0, WebMercator.latitudeToY(-WebMercator.MAX_LATITUDE), 1e-6)
    }

    @Test
    fun latitudesBeyondBound_clampInsteadOfDiverging() {
        assertClose(WebMercator.latitudeToY(WebMercator.MAX_LATITUDE), WebMercator.latitudeToY(90.0))
        assertClose(WebMercator.latitudeToY(-WebMercator.MAX_LATITUDE), WebMercator.latitudeToY(-90.0))
    }

    @Test
    fun roundTrip_recoversCoordinates() {
        val cities = listOf(48.8566 to 2.3522, -33.8688 to 151.2093, 37.7749 to -122.4194, 64.1466 to -21.9426)
        cities.forEach { (lat, lon) ->
            val p = WebMercator.toNormalized(lat, lon)
            assertClose(lat, WebMercator.yToLatitude(p.y), 1e-9)
            assertClose(lon, WebMercator.xToLongitude(p.x), 1e-9)
        }
    }

    @Test
    fun heading_cardinalDirections() {
        assertClose(0.0, WebMercator.heading(0.0, 0.0, 1.0, 0.0), 1e-6)
        assertClose(90.0, WebMercator.heading(0.0, 0.0, 0.0, 1.0), 1e-6)
        assertClose(180.0, WebMercator.heading(1.0, 0.0, 0.0, 0.0), 1e-6)
        assertClose(-90.0, WebMercator.heading(0.0, 0.0, 0.0, -1.0), 1e-6)
    }

    @Test
    fun offset_movesExpectedDistanceNorth() {
        // 111,319.5 m ≈ 1 degree of latitude on the WGS84 equatorial sphere (2πR / 360).
        val (lat, lon) = WebMercator.offset(0.0, 0.0, 111_319.5, 0.0)
        assertClose(1.0, lat, 1e-3)
        assertClose(0.0, lon, 1e-9)
    }

    @Test
    fun boundingBox_containsAllPointsWithPadding() {
        val points = listOf(NormalizedPoint(0.2, 0.3), NormalizedPoint(0.4, 0.7), NormalizedPoint(0.3, 0.5))
        val box = WebMercator.boundingBox(points, paddingRatio = 0.0)!!
        assertClose(0.2, box.xLeft)
        assertClose(0.4, box.xRight)
        assertClose(0.3, box.yTop)
        assertClose(0.7, box.yBottom)
        assertClose(0.3, box.centerX)
        assertClose(0.5, box.centerY)

        val padded = WebMercator.boundingBox(points, paddingRatio = 0.5)!!
        assertTrue(padded.xLeft < box.xLeft)
        assertTrue(padded.xRight > box.xRight)
    }

    @Test
    fun boundingBox_emptyInput_returnsNull() {
        assertNull(WebMercator.boundingBox(emptyList()))
    }

    @Test
    fun boundingBox_clampsToUnitSquare() {
        val box = WebMercator.boundingBox(listOf(NormalizedPoint(0.01, 0.99), NormalizedPoint(0.99, 0.01)))!!
        assertTrue(box.xLeft >= 0.0)
        assertTrue(box.yTop >= 0.0)
        assertTrue(box.xRight <= 1.0)
        assertTrue(box.yBottom <= 1.0)
    }

    @Test
    fun metersToNormalized_shrinkAtHighLatitude() {
        val atEquator = WebMercator.metersToNormalized(1000.0, 0.0)
        val atSixty = WebMercator.metersToNormalized(1000.0, 60.0)
        // cos(60°) = 0.5: the same metric distance spans twice the normalized width at 60° latitude.
        assertClose(atEquator * 2, atSixty, 1e-12)
    }

    @Test
    fun equals_forNormalizedPoint() {
        assertEquals(NormalizedPoint(0.5, 0.5), WebMercator.toNormalized(0.0, 0.0))
    }
}
