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

import org.meshtastic.feature.map.terrain.GeoBounds
import kotlin.math.cos
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The guard that keeps a sweep's terrain inside the shared cache.
 *
 * Tiles scale with the square of the radius and with 1/cos(latitude), so a fixed zoom is only right for one area. Past
 * the cache's capacity the failure is not graceful: eviction is insertion order, so the sweep evicts the tiles it is
 * about to read and re-decodes the disc on every pass.
 */
class TerrainZoomGuardTest {

    private fun boundsAround(latitudeDeg: Double, radiusKm: Double): GeoBounds {
        val latSpan = radiusKm / KM_PER_DEG_LAT
        val lonSpan = latSpan / cos(latitudeDeg * DEG_TO_RAD)
        return GeoBounds(
            south = latitudeDeg - latSpan,
            west = LONGITUDE - lonSpan,
            north = latitudeDeg + latSpan,
            east = LONGITUDE + lonSpan,
        )
    }

    @Test
    fun theOrdinaryCaseIsLeftAlone() {
        // A default-radius disc at mid latitudes already fits, so the guard must not cost it detail.
        val bounds = boundsAround(latitudeDeg = 47.6, radiusKm = 30.0)
        assertEquals(
            MapterhornElevation.DEFAULT_ZOOM,
            zoomFitting(MapterhornElevation.DEFAULT_ZOOM, bounds),
            "a 30 km disc at 47.6°N needs ${tilesSpanning(bounds, MapterhornElevation.DEFAULT_ZOOM)} tiles",
        )
    }

    @Test
    fun noRadiusOrLatitudeCanOverrunTheCache() {
        // The radius is a free-text field and the app runs well north of the mid latitudes, so both
        // of these are reachable without anyone doing anything unusual.
        for (latitudeDeg in listOf(0.0, 30.0, 45.0, 51.0, 60.0, 70.0, 78.0)) {
            for (radiusKm in listOf(5.0, 30.0, 50.0, 70.0, 100.0, 150.0, 300.0)) {
                val bounds = boundsAround(latitudeDeg, radiusKm)
                val zoom = zoomFitting(MapterhornElevation.DEFAULT_ZOOM, bounds)
                val tiles = tilesSpanning(bounds, zoom)
                assertTrue(
                    tiles <= TerrainCache.DEFAULT_CAPACITY,
                    "$latitudeDeg°N at $radiusKm km resolved to z$zoom, still $tiles tiles",
                )
            }
        }
    }

    @Test
    fun itNeverGoesDeeperThanAsked() {
        val tiny = boundsAround(latitudeDeg = 47.6, radiusKm = 1.0)
        assertTrue(zoomFitting(MapterhornElevation.DEFAULT_ZOOM, tiny) <= MapterhornElevation.DEFAULT_ZOOM)
    }

    @Test
    fun withoutBoundsThereIsNothingToFit() {
        assertEquals(MapterhornElevation.DEFAULT_ZOOM, zoomFitting(MapterhornElevation.DEFAULT_ZOOM, null))
    }

    private companion object {
        const val LONGITUDE = -122.3
        const val KM_PER_DEG_LAT = 111.32
        const val DEG_TO_RAD = 0.017453292519943295
    }
}
