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

import kotlinx.coroutines.flow.last
import kotlinx.coroutines.test.runTest
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class TerrainRegionExtractorTest {

    private val store = TerrainTileStore(FakeFileSystem(), "/regions/abc/terrain".toPath())

    /** Small enough to fit one z6 tile, so a regional archive URL resolves for it. */
    private val seattle = GeoBounds(south = 47.6, west = -122.4, north = 47.61, east = -122.39)

    private val regionalZoom = MapterhornEndpoints.GLOBAL_MAX_ZOOM + 2

    /** Serves a tile for every request when [hasCoverage], and none at all — sparse-archive style — otherwise. */
    private class FakeArchive(private val hasCoverage: Boolean) : TerrainRegionExtractor.TileArchive {
        override fun fetchTile(zoom: Int, x: Int, y: Int): ByteArray? = if (hasCoverage) byteArrayOf(1) else null

        override fun close() = Unit
    }

    private fun extractor(regionalHasCoverage: Boolean) = TerrainRegionExtractor(store) { url ->
        FakeArchive(hasCoverage = url == MapterhornEndpoints.GLOBAL_PMTILES_URL || regionalHasCoverage)
    }

    @Test
    fun `a regional archive that stores nothing does not report regional detail`() = runTest {
        val result = extractor(regionalHasCoverage = false).download(seattle, regionalZoom).last()

        assertIs<TerrainDownloadState.Complete>(result)
        assertEquals(false, result.hasRegionalDetail)
        assertTrue(result.tileCount > 0, "the global tier alone still counts")
        assertEquals(
            false,
            store.hasTile(TerrainSource.REGIONAL, TerrainTileMath.tileAt(regionalZoom, 47.605, -122.395)),
        )
    }

    @Test
    fun `a regional archive that stores tiles reports regional detail`() = runTest {
        val result = extractor(regionalHasCoverage = true).download(seattle, regionalZoom).last()

        assertIs<TerrainDownloadState.Complete>(result)
        assertEquals(true, result.hasRegionalDetail)
        assertTrue(store.hasTile(TerrainSource.REGIONAL, TerrainTileMath.tileAt(regionalZoom, 47.605, -122.395)))
    }
}
