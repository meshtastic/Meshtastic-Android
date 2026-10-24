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

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respondError
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import org.meshtastic.feature.map.terrain.ElevationTile
import org.meshtastic.feature.map.terrain.TerrainSource
import org.meshtastic.feature.map.terrain.TerrainTileStore
import org.meshtastic.feature.map.terrain.TileIndex
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull

class OfflineTerrainTest {

    private var requests = 0
    private val notFound =
        HttpClient(
            MockEngine {
                requests++
                respondError(HttpStatusCode.NotFound)
            },
        )
    private val region = TerrainTileStore(FakeFileSystem(), "/region".toPath())

    @Test
    fun aRegionTileIsReadWithoutTheNetwork() = runTest {
        val bytes = byteArrayOf(1, 2, 3)
        region.writeTile(TerrainSource.GLOBAL, TileIndex(12, 655, 1430), bytes)

        val read = MapterhornTiles(notFound, store = null, offline = listOf(region)).bytes(12, 655, 1430)

        assertContentEquals(bytes, read)
        assertEquals(0, requests)
    }

    @Test
    fun aTileNoRegionHoldsStillGoesToTheNetwork() = runTest {
        val read = MapterhornTiles(notFound, store = null, offline = listOf(region)).bytes(12, 655, 1430)

        assertNull(read)
        assertEquals(1, requests)
    }

    @Test
    fun aDescendantIsItsQuarterOfTheParent() {
        val parent = ElevationTile(4, 4, FloatArray(16) { it.toFloat() })

        // Tile (3, 2) one zoom down is the parent's top-right quarter.
        val child = parent.descendant(levels = 1, x = 3, y = 2)

        assertEquals(2, child.width)
        assertEquals(2, child.height)
        assertContentEquals(floatArrayOf(2f, 3f, 6f, 7f), child.elevations)
    }

    @Test
    fun aDescendantFinerThanThePixelsIsOnePixel() {
        val parent = ElevationTile(2, 2, floatArrayOf(10f, 20f, 30f, 40f))

        val child = parent.descendant(levels = 3, x = 7, y = 0)

        assertContentEquals(floatArrayOf(20f), child.elevations)
    }
}
