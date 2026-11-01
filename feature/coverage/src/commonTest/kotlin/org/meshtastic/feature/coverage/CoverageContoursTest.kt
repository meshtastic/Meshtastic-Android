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
package org.meshtastic.feature.coverage

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class CoverageContoursTest {

    @Test
    fun aSiteNameWithQuotesAndBackslashesStaysOneJsonString() {
        val site =
            Site(
                name = "Tom's \"Hill\" \\ relay",
                latitude = 47.6,
                longitude = -122.2,
                frequencyMhz = 915.0,
                txPowerDbm = 30.0,
                rxSensitivityDbm = -130.0,
            )
        val grid =
            CoverageGrid(
                site = site,
                width = 2,
                height = 2,
                north = 47.61,
                south = 47.59,
                east = -122.19,
                west = -122.21,
                dbm = DoubleArray(4) { -90.0 },
            )

        val geoJson = grid.toGeoJson()

        assertTrue("\"name\": \"Tom's \\\"Hill\\\" \\\\ relay\"" in geoJson, geoJson.take(300))
    }

    @Test
    fun theOuterEdgeOfAnEstimateIsACircleAtItsRadius() {
        val grid = disc { -90.0 }

        val ring = grid.bands(listOf(-130.0)).single().polygons.single().single()

        // A cell is ~0.32 km here, so a staircase edge would miss the radius by up to ~0.2 km.
        assertAllAtKm(ring, RADIUS_KM, tolerance = 0.02)
    }

    @Test
    fun aBandEdgeFollowsTheThresholdBetweenSamples() {
        // Signal falls 5 dB per km, so -85 dBm sits at exactly 5 km.
        val grid = disc { km -> -60.0 - 5.0 * km }

        val strong = grid.bands(listOf(-130.0, -85.0)).last()

        assertAllAtKm(strong.polygons.single().single(), 5.0, tolerance = 0.05)
    }

    @Test
    fun eachBandIsCutOutOfTheOneBelowSoTheyTileTheDisc() {
        val grid = disc { km -> -60.0 - 5.0 * km }

        val (weak, strong) = grid.bands(listOf(-130.0, -85.0))

        val (outer, hole) = weak.polygons.single()
        assertAllAtKm(outer, RADIUS_KM, tolerance = 0.02)
        // The same points in the opposite direction: the bands share the edge exactly, with no gap or overlap.
        val strongOuter = strong.polygons.single().single()
        assertEquals(strongOuter.toSet(), hole.toSet())
        assertEquals(-signedArea(strongOuter), signedArea(hole), absoluteTolerance = 1e-12)
    }

    @Test
    fun aBandThatCoversTheWholeDiscLeavesTheBandBelowItEmpty() {
        val grid = disc { -90.0 }

        val bands = grid.bands(listOf(-130.0, -100.0))

        assertEquals(listOf(-100.0), bands.map { it.thresholdDbm })
    }

    @Test
    fun outerRingsRunCounterclockwiseAndHolesClockwise() {
        val grid = disc { km -> -60.0 - 5.0 * km }

        val (outer, hole) = grid.bands(listOf(-130.0, -85.0)).first().polygons.single()

        assertTrue(signedArea(outer) > 0, "outer ring should be counterclockwise")
        assertTrue(signedArea(hole) < 0, "hole should be clockwise")
    }

    private fun disc(dbmAtKm: (Double) -> Double): CoverageGrid {
        val rings = 41
        val step = RADIUS_KM / (rings - 1)
        val polar =
            PolarCoverage(
                DISC_SITE,
                RADIALS,
                rings,
                0.0,
                step,
                DoubleArray(RADIALS * rings) { dbmAtKm(it % rings * step) },
            )
        return polar.toGrid(resolution = 64)
    }

    private fun assertAllAtKm(ring: CoverageRing, km: Double, tolerance: Double) {
        for ((lon, lat) in ring) {
            val distance = haversineKm(DISC_SITE.latitude, DISC_SITE.longitude, lat, lon)
            assertTrue(abs(distance - km) < tolerance, "point at $distance km, expected $km")
        }
    }

    private fun signedArea(ring: CoverageRing): Double =
        ring.zipWithNext { (x1, y1), (x2, y2) -> x1 * y2 - x2 * y1 }.sum() / 2

    private companion object {
        const val RADIUS_KM = 10.0
        const val RADIALS = 180
        val DISC_SITE = Site("Disc", 47.6, -122.2, 915.0, 30.0, -130.0, radiusKm = RADIUS_KM)
    }

    @Test
    fun aGridOneSampleWideIsRejected() {
        assertFailsWith<IllegalArgumentException> {
            CoverageGrid(
                site = Site("Test", 10.0, 26.0, 915.0, 30.0, -130.0),
                width = 1,
                height = 3,
                north = 12.0,
                south = 8.0,
                east = 26.0,
                west = 26.0,
                dbm = DoubleArray(3) { -90.0 },
            )
        }
    }
}
