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
package org.meshtastic.feature.map.mapcompose

import kotlinx.coroutines.test.runTest
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import org.meshtastic.feature.map.mapcompose.tile.TileDiskCache
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertNull

@Suppress("MagicNumber")
class TileDiskCacheTest {

    private val root = "/tiles".toPath()

    private fun cache(fs: FakeFileSystem = FakeFileSystem(), maxBytes: Long = 1024): TileDiskCache =
        TileDiskCache(fs, root, maxBytes)

    @Test fun miss_returnsNull() = runTest { assertNull(cache().read("osm", 10, 1, 2)) }

    @Test
    fun writeThenRead_roundTrips() = runTest {
        val cache = cache()
        val bytes = byteArrayOf(1, 2, 3, 4)
        cache.write("osm", 10, 1, 2, bytes)
        assertContentEquals(bytes, cache.read("osm", 10, 1, 2))
    }

    @Test
    fun keys_isolateSourcesAndCoordinates() = runTest {
        val cache = cache()
        cache.write("osm", 10, 1, 2, byteArrayOf(1))
        cache.write("esri", 10, 1, 2, byteArrayOf(2))
        cache.write("osm", 10, 2, 1, byteArrayOf(3))
        assertContentEquals(byteArrayOf(1), cache.read("osm", 10, 1, 2))
        assertContentEquals(byteArrayOf(2), cache.read("esri", 10, 1, 2))
        assertContentEquals(byteArrayOf(3), cache.read("osm", 10, 2, 1))
        assertNull(cache.read("osm", 11, 1, 2))
    }

    @Test
    fun eviction_dropsLeastRecentlyUsedFirst() = runTest {
        val cache = cache(maxBytes = 10)
        cache.write("s", 1, 0, 0, ByteArray(4))
        cache.write("s", 1, 0, 1, ByteArray(4))
        // Touch the first entry so the second becomes least recently used.
        cache.read("s", 1, 0, 0)
        // 4 + 4 + 4 > 10: one entry must go, and it must be (0,1).
        cache.write("s", 1, 0, 2, ByteArray(4))
        assertContentEquals(ByteArray(4), cache.read("s", 1, 0, 0))
        assertNull(cache.read("s", 1, 0, 1))
        assertContentEquals(ByteArray(4), cache.read("s", 1, 0, 2))
    }

    @Test
    fun oversizedWrite_evictsEverythingElseButStores() = runTest {
        val cache = cache(maxBytes = 10)
        cache.write("s", 1, 0, 0, ByteArray(4))
        cache.write("s", 1, 0, 1, ByteArray(12))
        assertNull(cache.read("s", 1, 0, 0))
    }

    @Test
    fun persistedEntries_surviveNewCacheInstance() = runTest {
        val fs = FakeFileSystem()
        val bytes = byteArrayOf(9, 8, 7)
        cache(fs).write("osm", 5, 3, 4, bytes)
        // A fresh instance over the same file system rebuilds its index from disk.
        assertContentEquals(bytes, TileDiskCache(fs, root, 1024).read("osm", 5, 3, 4))
    }

    @Test
    fun corruptEntry_readAsMissAfterDeletion() = runTest {
        val fs = FakeFileSystem()
        val cache = cache(fs)
        cache.write("osm", 5, 3, 4, byteArrayOf(1, 2))
        fs.delete(root / "osm/5/3/4")
        assertNull(cache.read("osm", 5, 3, 4))
        // And the cache stays usable afterwards.
        cache.write("osm", 5, 3, 4, byteArrayOf(5))
        assertContentEquals(byteArrayOf(5), cache.read("osm", 5, 3, 4))
    }
}
