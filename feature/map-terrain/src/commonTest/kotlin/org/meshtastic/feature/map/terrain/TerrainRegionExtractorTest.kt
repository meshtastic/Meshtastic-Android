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

    /** Inside the single z6 tile 10/22. */
    private val seattle = GeoBounds(south = 47.6, west = -122.4, north = 47.61, east = -122.39)

    /** Straddles the line between z6 tiles 9/22 and 10/22 at -123.75. */
    private val straddling = GeoBounds(south = 47.6, west = -123.76, north = 47.61, east = -123.74)

    private val regionalZoom = MapterhornEndpoints.GLOBAL_MAX_ZOOM + 2

    /** Serves a tile for every request when [hasCoverage], and none at all — sparse-archive style — otherwise. */
    private class FakeArchive(
        private val hasCoverage: Boolean,
        private val requested: MutableList<TileIndex> = mutableListOf(),
    ) : TerrainRegionExtractor.TileArchive {
        override fun fetchTile(zoom: Int, x: Int, y: Int): ByteArray? {
            requested += TileIndex(zoom, x, y)
            return if (hasCoverage) byteArrayOf(1) else null
        }

        override fun close() = Unit
    }

    private enum class Regional {
        MISSING,
        EMPTY,
        COVERED,
    }

    private fun extractor(regional: Regional) = TerrainRegionExtractor(store) { url ->
        when {
            url == MapterhornEndpoints.GLOBAL_PMTILES_URL -> FakeArchive(hasCoverage = true)
            regional == Regional.MISSING -> null
            else -> FakeArchive(hasCoverage = regional == Regional.COVERED)
        }
    }

    @Test
    fun `a missing regional archive keeps the global tier and reports no regional detail`() = runTest {
        val result = extractor(Regional.MISSING).download(seattle, regionalZoom).last()

        assertIs<TerrainDownloadState.Complete>(result)
        assertEquals(false, result.hasRegionalDetail)
        assertTrue(store.hasTile(TerrainSource.GLOBAL, TerrainTileMath.tileAt(12, 47.605, -122.395)))
    }

    @Test
    fun `a region crossing a z6 line reads each regional archive for its own tiles only`() = runTest {
        val requestsByUrl = mutableMapOf<String, MutableList<TileIndex>>()
        val extractor =
            TerrainRegionExtractor(store) { url ->
                FakeArchive(hasCoverage = true, requested = requestsByUrl.getOrPut(url) { mutableListOf() })
            }

        val result = extractor.download(straddling, regionalZoom).last()

        assertIs<TerrainDownloadState.Complete>(result)
        assertEquals(true, result.hasRegionalDetail)
        val west = MapterhornEndpoints.regionalArchiveUrl(TileIndex(6, 9, 22))
        val east = MapterhornEndpoints.regionalArchiveUrl(TileIndex(6, 10, 22))
        assertEquals(setOf(MapterhornEndpoints.GLOBAL_PMTILES_URL, west, east), requestsByUrl.keys)
        assertTrue(requestsByUrl.getValue(west).all { TerrainTileMath.ancestorAt(6, it) == TileIndex(6, 9, 22) })
        assertTrue(requestsByUrl.getValue(east).all { TerrainTileMath.ancestorAt(6, it) == TileIndex(6, 10, 22) })
        assertTrue(store.hasTile(TerrainSource.REGIONAL, TerrainTileMath.tileAt(regionalZoom, 47.605, -123.755)))
        assertTrue(store.hasTile(TerrainSource.REGIONAL, TerrainTileMath.tileAt(regionalZoom, 47.605, -123.745)))
    }

    @Test
    fun `a regional archive that stores nothing does not report regional detail`() = runTest {
        val result = extractor(Regional.EMPTY).download(seattle, regionalZoom).last()

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
        val result = extractor(Regional.COVERED).download(seattle, regionalZoom).last()

        assertIs<TerrainDownloadState.Complete>(result)
        assertEquals(true, result.hasRegionalDetail)
        assertTrue(store.hasTile(TerrainSource.REGIONAL, TerrainTileMath.tileAt(regionalZoom, 47.605, -122.395)))
    }

    @Test
    fun `a completed download names each archive that supplied tiles with its version`() = runTest {
        val extractor =
            TerrainRegionExtractor(store) { url ->
                object : TerrainRegionExtractor.TileArchive {
                    override val version: String = "etag-of-$url"

                    override fun fetchTile(zoom: Int, x: Int, y: Int): ByteArray = byteArrayOf(1)

                    override fun close() = Unit
                }
            }

        val result = extractor.download(straddling, regionalZoom).last()

        assertIs<TerrainDownloadState.Complete>(result)
        val global = MapterhornEndpoints.GLOBAL_PMTILES_URL
        val west = MapterhornEndpoints.regionalArchiveUrl(TileIndex(6, 9, 22))
        val east = MapterhornEndpoints.regionalArchiveUrl(TileIndex(6, 10, 22))
        assertEquals(
            mapOf(global to "etag-of-$global", west to "etag-of-$west", east to "etag-of-$east"),
            result.archives,
        )
    }

    @Test
    fun `a global archive that stores nothing is not named as a source`() = runTest {
        val extractor =
            TerrainRegionExtractor(store) { _ ->
                object : TerrainRegionExtractor.TileArchive {
                    override fun fetchTile(zoom: Int, x: Int, y: Int): ByteArray? = null

                    override fun close() = Unit
                }
            }

        val result = extractor.download(seattle, MapterhornEndpoints.GLOBAL_MAX_ZOOM).last()

        assertIs<TerrainDownloadState.Complete>(result)
        assertEquals(emptyMap(), result.archives)
    }

    @Test
    fun `a regional archive that stores nothing is not named as a source`() = runTest {
        val result = extractor(Regional.EMPTY).download(seattle, regionalZoom).last()

        assertIs<TerrainDownloadState.Complete>(result)
        assertEquals(setOf(MapterhornEndpoints.GLOBAL_PMTILES_URL), result.archives.keys)
    }

    /**
     * For the first [changes] archives opened, serves one tile and then throws [ArchiveChangedException], so the
     * download has stored something before the archive changes under it.
     */
    private fun changingExtractor(changes: Int): TerrainRegionExtractor {
        var remaining = changes
        return TerrainRegionExtractor(store) { _ ->
            val changesNow = remaining-- > 0
            var fetched = 0
            object : TerrainRegionExtractor.TileArchive {
                override fun fetchTile(zoom: Int, x: Int, y: Int): ByteArray? =
                    if (changesNow && fetched++ > 0) throw ArchiveChangedException() else byteArrayOf(1)

                override fun close() = Unit
            }
        }
    }

    @Test
    fun `an archive replaced mid-download is fetched again once`() = runTest {
        val result = changingExtractor(changes = 1).download(seattle, MapterhornEndpoints.GLOBAL_MAX_ZOOM).last()

        assertIs<TerrainDownloadState.Complete>(result)
        assertTrue(store.hasTile(TerrainSource.GLOBAL, TerrainTileMath.tileAt(12, 47.605, -122.395)))
    }

    @Test
    fun `an archive replaced twice fails and keeps nothing`() = runTest {
        val result = changingExtractor(changes = 2).download(seattle, MapterhornEndpoints.GLOBAL_MAX_ZOOM).last()

        assertEquals(TerrainDownloadState.Failed(TerrainDownloadFailure.IO_ERROR), result)
        assertEquals(0L, store.sizeBytes())
    }
}
