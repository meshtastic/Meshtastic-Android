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

import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TerrainTileStoreTest {

    private val fileSystem = FakeFileSystem()
    private val store = TerrainTileStore(fileSystem, "/regions/abc/terrain".toPath())

    @Test
    fun `a written tile reads back byte-for-byte`() {
        val tile = TileIndex(zoom = 10, x = 163, y = 353)
        val bytes = byteArrayOf(1, 2, 3, 4)

        store.writeTile(TerrainSource.GLOBAL, tile, bytes)

        assertTrue(store.hasTile(TerrainSource.GLOBAL, tile))
        assertEquals(bytes.toList(), store.readTile(TerrainSource.GLOBAL, tile)?.toList())
    }

    @Test
    fun `an unwritten tile reads as null not an error`() {
        assertNull(store.readTile(TerrainSource.GLOBAL, TileIndex(5, 1, 1)))
        assertEquals(false, store.hasTile(TerrainSource.REGIONAL, TileIndex(5, 1, 1)))
    }

    @Test
    fun `global and regional tiles at the same coordinates don't collide`() {
        val tile = TileIndex(zoom = 14, x = 10, y = 10)
        store.writeTile(TerrainSource.GLOBAL, tile, byteArrayOf(1))
        store.writeTile(TerrainSource.REGIONAL, tile, byteArrayOf(2))

        assertEquals(listOf(1.toByte()), store.readTile(TerrainSource.GLOBAL, tile)?.toList())
        assertEquals(listOf(2.toByte()), store.readTile(TerrainSource.REGIONAL, tile)?.toList())
    }

    @Test
    fun `sizeBytes sums every tile written across both sources`() {
        store.writeTile(TerrainSource.GLOBAL, TileIndex(5, 1, 1), ByteArray(100))
        store.writeTile(TerrainSource.REGIONAL, TileIndex(14, 2, 2), ByteArray(250))
        assertEquals(350L, store.sizeBytes())
    }

    @Test
    fun `sizeBytes is zero for a store nothing has been written to yet`() {
        assertEquals(0L, store.sizeBytes())
    }

    @Test
    fun `deleteAll removes every tile and leaves the store readable-empty`() {
        store.writeTile(TerrainSource.GLOBAL, TileIndex(5, 1, 1), byteArrayOf(1))
        store.deleteAll()

        assertEquals(0L, store.sizeBytes())
        assertNull(store.readTile(TerrainSource.GLOBAL, TileIndex(5, 1, 1)))
    }

    @Test
    fun `deleteAll on a store nothing was ever written to is a harmless no-op`() {
        store.deleteAll()
        assertEquals(0L, store.sizeBytes())
    }
}
