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
import org.meshtastic.feature.map.terrain.ElevationTile
import kotlin.test.Test
import kotlin.test.assertEquals

class SharedTerrainReleaseTest {

    private val tile = ElevationTile(1, 1, floatArrayOf(5f))

    private suspend fun warm(zoom: Int) {
        SharedTerrain.forArchive(ARCHIVE, zoom).publish(listOf(1L to tile, 2L to tile))
    }

    @Test
    fun clearingACacheDropsItsTiles() = runTest {
        val cache = TerrainCache()
        cache.publish(listOf(1L to tile))
        cache.clear()
        assertEquals(0, cache.size)
    }

    @Test
    fun terrainIsDroppedWhenTheEstimateEnds() = runTest {
        SharedTerrain.estimateStarted()
        warm(zoom = 11)
        assertEquals(2, SharedTerrain.residentTiles())

        SharedTerrain.estimateFinished()

        assertEquals(0, SharedTerrain.residentTiles())
    }

    @Test
    fun terrainIsKeptWhileAnotherEstimateIsStillRunning() = runTest {
        SharedTerrain.estimateStarted()
        SharedTerrain.estimateStarted()
        warm(zoom = 13)

        SharedTerrain.estimateFinished()
        assertEquals(2, SharedTerrain.residentTiles())

        SharedTerrain.estimateFinished()
        assertEquals(0, SharedTerrain.residentTiles())
    }

    @Test
    fun coordinatesAreWrittenAtSixDecimalPlacesWithoutPadding() {
        assertEquals("-98.526", (-98.526).toCoordinate())
        assertEquals("29.422912", 29.4229123456789.toCoordinate())
        assertEquals("0.000001", 0.0000006.toCoordinate())
        assertEquals("12", 12.0.toCoordinate())
        assertEquals("-0.5", (-0.5).toCoordinate())
        assertEquals("0", (-0.0000001).toCoordinate())
    }

    private companion object {
        const val ARCHIVE = "test://shared-terrain-release"
    }
}
