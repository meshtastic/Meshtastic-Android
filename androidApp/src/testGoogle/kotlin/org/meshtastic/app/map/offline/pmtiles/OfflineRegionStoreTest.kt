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
package org.meshtastic.app.map.offline.pmtiles

import kotlinx.coroutines.test.runTest
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OfflineRegionStoreTest {

    private lateinit var baseDir: File
    private lateinit var store: OfflineRegionStore

    @BeforeTest
    fun setUp() {
        baseDir =
            File.createTempFile("offline-region-store", "").apply {
                delete()
                mkdirs()
            }
        store = OfflineRegionStore(baseDir)
    }

    @AfterTest
    fun tearDown() {
        baseDir.deleteRecursively()
    }

    @Test
    fun `a manifest written before terrain existed still decodes, defaulting to no terrain`() {
        // What OfflineRegionStore itself wrote before hasTerrain/terrainByteSize/terrainHasRegionalDetail existed —
        // no trace of those fields at all, which is exactly what a manifest already on a user's device looks like.
        File(baseDir, "manifest.json")
            .writeText(
                """
                [{"id":"abc","southLat":1.0,"westLon":2.0,"northLat":3.0,"eastLon":4.0,"minZoom":5,"maxZoom":6,
                "tileCount":7,"byteSize":8,"createdAtEpochSeconds":9}]
                """
                    .trimIndent(),
            )

        val regions = store.list()

        assertEquals(1, regions.size)
        val region = regions.single()
        assertEquals("abc", region.id)
        assertFalse(region.hasTerrain)
        assertEquals(0L, region.terrainByteSize)
        assertFalse(region.terrainHasRegionalDetail)
    }

    @Test
    fun `totalBytes sums both the base archive and any terrain attached to it`() = runTest {
        store.add(baseRegion(id = "a", byteSize = 100L, terrainByteSize = 0L))
        store.add(baseRegion(id = "b", byteSize = 200L, terrainByteSize = 50L))

        assertEquals(350L, store.totalBytes())
    }

    @Test
    fun `deleting a region also deletes its terrain directory`() = runTest {
        store.add(baseRegion(id = "abc"))
        val terrainDir = store.terrainDir("abc")
        terrainDir.mkdirs()
        File(terrainDir, "global/5/1/1.webp").apply { parentFile?.mkdirs() }.writeBytes(byteArrayOf(1, 2, 3))
        assertTrue(terrainDir.exists())

        store.delete("abc")

        assertFalse(terrainDir.exists())
        assertTrue(store.list().isEmpty())
    }

    @Test
    fun `add replaces an existing region with the same id, terrain fields included`() = runTest {
        store.add(baseRegion(id = "abc", hasTerrain = false))
        store.add(baseRegion(id = "abc", hasTerrain = true, terrainByteSize = 42L))

        val regions = store.list()

        assertEquals(1, regions.size)
        assertTrue(regions.single().hasTerrain)
        assertEquals(42L, regions.single().terrainByteSize)
    }

    private fun baseRegion(id: String, byteSize: Long = 0L, hasTerrain: Boolean = false, terrainByteSize: Long = 0L) =
        OfflineRegion(
            id = id,
            southLat = 0.0,
            westLon = 0.0,
            northLat = 1.0,
            eastLon = 1.0,
            minZoom = 5,
            maxZoom = 10,
            tileCount = 1,
            byteSize = byteSize,
            createdAtEpochSeconds = 0L,
            hasTerrain = hasTerrain,
            terrainByteSize = terrainByteSize,
        )
}
