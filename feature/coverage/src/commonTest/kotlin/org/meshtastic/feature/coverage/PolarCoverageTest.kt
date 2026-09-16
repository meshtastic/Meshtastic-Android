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

import kotlinx.coroutines.test.runTest
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The polar sweep and the grid it is resampled onto.
 *
 * Coverage is computed per bearing and only then rasterised, so these cover the seam between the two: that the lattice
 * is swept where it claims to be, and that resampling it preserves the prediction rather than inventing structure of
 * its own.
 */
class PolarCoverageTest {

    private val flat = ElevationSource { _, _ -> 0.0 }

    private fun site(radiusKm: Double = 10.0) = Site(
        name = "Test",
        latitude = 47.6,
        longitude = -122.2,
        frequencyMhz = 915.0,
        txPowerDbm = 30.0,
        rxSensitivityDbm = -130.0,
        txHeightM = 10.0,
        radiusKm = radiusKm,
    )

    @Test
    fun ringsStepOutwardsAndStayInsideTheRadius() = runTest {
        val polar = LocalCoverage(flat).sweepPolar(site(), radials = 16, rings = 12)
        val distances = (0 until polar.rings).map { polar.ringKm(it) }
        assertTrue(distances.zipWithNext().all { (a, b) -> b > a }, "rings should step outward: $distances")
        assertTrue(distances.first() > 0.0, "the innermost ring cannot sit on the transmitter")
        assertTrue(distances.last() <= site().radiusKm + 1e-9, "outermost ring ${distances.last()} exceeds the radius")
    }

    @Test
    fun overFlatGroundStrengthDependsOnRangeAlone() = runTest {
        // No terrain means no bearing can differ from any other; anything that does is the sweep's
        // own geometry leaking in.
        val polar = LocalCoverage(flat).sweepPolar(site(), radials = 24, rings = 10)
        for (r in 0 until polar.rings) {
            val onRing = (0 until polar.radials).map { polar.at(it, r) }
            val spread = onRing.max() - onRing.min()
            assertTrue(spread < 0.5, "ring $r varies by $spread dB across bearings over flat ground")
        }
    }

    @Test
    fun signalFallsOffWithRange() = runTest {
        val polar = LocalCoverage(flat).sweepPolar(site(), radials = 8, rings = 16)
        val alongOneBearing = (0 until polar.rings).map { polar.at(0, it) }
        assertTrue(
            alongOneBearing.first() > alongOneBearing.last(),
            "signal should be weaker far away: ${alongOneBearing.first()} -> ${alongOneBearing.last()}",
        )
    }

    @Test
    fun gridCoversTheDiscAndNothingOutsideIt() = runTest {
        val grid = LocalCoverage(flat).sweepPolar(site(), radials = 16, rings = 12).toGrid(resolution = 48)
        assertEquals(48, grid.width)
        assertEquals(48, grid.height)
        for (y in 0 until grid.height) {
            for (x in 0 until grid.width) {
                val km =
                    haversineKm(site().latitude, site().longitude, grid.latAt(y.toDouble()), grid.lonAt(x.toDouble()))
                val computed = !grid.at(x, y).isNaN()
                // The corners of a square box are outside a disc inscribed in it; nothing else is.
                assertEquals(km <= site().radiusKm, computed, "cell ($x,$y) at ${km}km computed=$computed")
            }
        }
    }

    @Test
    fun resamplingFinerDoesNotMoveThePrediction() = runTest {
        // The grid is free of the prediction budget, so a finer one has to show the same coverage —
        // if it did not, the extra cells would be the resampler inventing detail.
        val polar = LocalCoverage(flat).sweepPolar(site(), radials = 32, rings = 16)
        val coarse = polar.toGrid(resolution = 32).reachableFraction
        val fine = polar.toGrid(resolution = 192).reachableFraction
        assertTrue(abs(coarse - fine) < 0.02, "reachable fraction moved with resolution: $coarse vs $fine")
    }

    @Test
    fun gridIsSymmetricAboutTheSiteOverFlatGround() = runTest {
        val grid = LocalCoverage(flat).sweepPolar(site(), radials = 32, rings = 16).toGrid(resolution = 65)
        val mid = 32 // the centre cell of an odd-sized grid sits on the site
        for (d in 1..mid) {
            val west = grid.at(mid - d, mid)
            val east = grid.at(mid + d, mid)
            if (west.isNaN() || east.isNaN()) continue
            assertTrue(abs(west - east) < 0.5, "cells $d west and east differ: $west vs $east")
        }
    }
}
