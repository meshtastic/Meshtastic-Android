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
package org.meshtastic.feature.map.maplibre.terrain

import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import org.meshtastic.feature.map.terrain.GeoBounds
import org.meshtastic.feature.map.terrain.TerrainSource
import org.meshtastic.feature.map.terrain.TerrainTileStore
import org.meshtastic.feature.map.terrain.TileIndex
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OfflineTerrainRepositoryTest {

    private val fileSystem = FakeFileSystem()
    private val baseDir = "/terrain".toPath()

    private fun repository() = OfflineTerrainRepository(fileSystem, baseDir)

    @Test
    fun `region is null before anything is downloaded`() = runTest {
        val repository = repository()
        repository.refresh()
        assertNull(repository.region.value)
    }

    // A single-tile, single-zoom bounding box that fits in one z6 archive, so the fake extractor's fetch below is a
    // network-free, single-tile round trip through TerrainTileStore rather than a real download.
    private val tinyBounds = GeoBounds(south = 47.6, west = -122.4, north = 47.61, east = -122.39)

    @Test
    fun `an orphaned tile directory with no manifest is cleared on load`() = runTest {
        // Simulates a process death mid-download: tiles on disk, no manifest ever written.
        val store = TerrainTileStore(fileSystem, baseDir / "tiles")
        store.writeTile(TerrainSource.GLOBAL, TileIndex(5, 1, 1), byteArrayOf(1, 2, 3))
        assertTrue(store.sizeBytes() > 0L)

        val repository = repository()
        repository.refresh()

        assertNull(repository.region.value)
        assertEquals(0L, store.sizeBytes())
    }

    @Test
    fun `a manifest with no tile directory still loads as a region`() = runTest {
        // The manifest and the tile directory are independent on disk; this only exercises manifest read/write, not
        // the orphan-tile cleanup above.
        val repository = repository()
        writeManifestDirectly(
            OfflineTerrainRegion(
                south = tinyBounds.south,
                west = tinyBounds.west,
                north = tinyBounds.north,
                east = tinyBounds.east,
                maxZoom = 10,
                hasRegionalDetail = false,
                tileCount = 3,
                byteSize = 900,
            ),
        )

        repository.refresh()

        val region = repository.region.value
        assertEquals(3L, region?.tileCount)
        assertEquals(900L, region?.byteSize)
    }

    @Test
    fun `delete clears both the manifest and the region state`() = runTest {
        val repository = repository()
        writeManifestDirectly(
            OfflineTerrainRegion(
                south = 0.0,
                west = 0.0,
                north = 1.0,
                east = 1.0,
                maxZoom = 10,
                hasRegionalDetail = false,
                tileCount = 1,
                byteSize = 1,
            ),
        )
        repository.refresh()
        assertTrue(repository.region.value != null)

        repository.delete()

        assertNull(repository.region.value)
        assertTrue(!fileSystem.exists(baseDir / "manifest.json"))
    }

    @Test
    fun `download with zero tiles still emits Complete and persists an empty region`() = runTest {
        val repository = repository()
        // A box at maxZoom -1 requests no zoom levels at all, so TerrainRegionExtractor's own totalTiles==0 short
        // circuit fires with no network access — the same "zero is a real, successful outcome" case its own flow
        // documents.
        val states = repository.download(tinyBounds, maxZoom = -1).toList()

        assertEquals(1, states.size)
        val region = repository.region.value
        assertEquals(0L, region?.tileCount)
        assertTrue(fileSystem.exists(baseDir / "manifest.json"))
    }

    @Test
    fun `tileUrlTemplate is an absolute three-slash file url with z x y placeholders`() {
        val repository = OfflineTerrainRepository(fileSystem, "/data/terrain".toPath())

        assertEquals(
            "file:///data/terrain/tiles/global/{z}/{x}/{y}.webp",
            repository.tileUrlTemplate(TerrainSource.GLOBAL),
        )
        assertEquals(
            "file:///data/terrain/tiles/regional/{z}/{x}/{y}.webp",
            repository.tileUrlTemplate(TerrainSource.REGIONAL),
        )
    }

    private fun writeManifestDirectly(region: OfflineTerrainRegion) {
        val manifestPath = baseDir / "manifest.json"
        fileSystem.createDirectories(baseDir)
        fileSystem.write(manifestPath) { writeUtf8(Json.encodeToString(region)) }
    }
}
