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

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlinx.io.buffered
import kotlinx.io.readByteArray
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import org.meshtastic.feature.map.mapcompose.tile.MeshTileStreamProvider
import org.meshtastic.feature.map.mapcompose.tile.TileDiskCache
import org.meshtastic.feature.map.mapcompose.tile.TileSourceCatalog
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@Suppress("MagicNumber")
class MeshTileStreamProviderTest {

    private val tileBytes = byteArrayOf(1, 2, 3)

    private fun cache() = TileDiskCache(FakeFileSystem(), "/tiles".toPath(), 1024)

    private fun provider(cache: TileDiskCache, engine: MockEngine): MeshTileStreamProvider =
        MeshTileStreamProvider(TileSourceCatalog.OSM_MAPNIK, cache, HttpClient(engine), userAgent = "Meshtastic-Test")

    @Test
    fun fetch_success_returnsStreamAndCaches() = runTest {
        var requests = 0
        val engine = MockEngine { request ->
            requests++
            assertEquals("Meshtastic-Test", request.headers["User-Agent"])
            assertEquals("https://tile.openstreetmap.org/10/163/395.png", request.url.toString())
            respond(tileBytes)
        }
        val cache = cache()
        val provider = provider(cache, engine)

        val first = provider.getTileStream(row = 395, col = 163, zoomLvl = 10)
        assertContentEquals(tileBytes, first!!.buffered().readByteArray())

        // Second call is served from the disk cache: no extra network request.
        val second = provider.getTileStream(row = 395, col = 163, zoomLvl = 10)
        assertContentEquals(tileBytes, second!!.buffered().readByteArray())
        assertEquals(1, requests)
    }

    @Test
    fun httpError_returnsNullAndDoesNotCache() = runTest {
        var requests = 0
        val engine = MockEngine {
            requests++
            respondError(HttpStatusCode.NotFound)
        }
        val provider = provider(cache(), engine)
        assertNull(provider.getTileStream(1, 1, 1))
        assertNull(provider.getTileStream(1, 1, 1))
        assertEquals(2, requests)
    }

    @Test
    fun networkException_isContainedToNull() = runTest {
        val engine = MockEngine { throw RuntimeException("socket reset") }
        val provider = provider(cache(), engine)
        assertNull(provider.getTileStream(2, 2, 2))
    }

    @Test
    fun cacheHit_skipsNetworkEntirely() = runTest {
        val cache = cache()
        cache.write(TileSourceCatalog.OSM_MAPNIK.id, 3, 4, 5, tileBytes)
        var requests = 0
        val engine = MockEngine {
            requests++
            respond(byteArrayOf(9))
        }
        val provider = provider(cache, engine)
        val stream = provider.getTileStream(row = 4, col = 5, zoomLvl = 3)
        assertContentEquals(tileBytes, stream!!.buffered().readByteArray())
        assertTrue(requests == 0)
    }
}
