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
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsBytes
import io.ktor.http.isSuccess
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.meshtastic.core.common.util.ioDispatcher
import org.meshtastic.feature.map.terrain.MapterhornEndpoints
import org.meshtastic.feature.map.terrain.TerrainSource
import org.meshtastic.feature.map.terrain.TerrainTileStore
import org.meshtastic.feature.map.terrain.TileIndex

/**
 * Terrarium tile bytes, from local storage if they are there and from Mapterhorn's XYZ endpoint if they are not.
 *
 * Plain per-tile requests rather than range reads into `planet.pmtiles`. The archive is one seekable channel, so its
 * tiles come back strictly one at a time — measured at ~300 ms each, which for a 25 km disc was the whole cost of a
 * coverage sweep. These are independent, Cloudflare-cached, and suspend rather than block, so the only limit is how
 * many we choose to have in flight.
 *
 * Anything fetched is written back to [store] when there is one, so the next run — or the next launch — pays nothing
 * for the same ground.
 */
internal class MapterhornTiles(
    private val http: HttpClient,
    private val store: TerrainTileStore?,
    concurrency: Int = DEFAULT_CONCURRENCY,
) {
    private val gate = Semaphore(concurrency)

    /** Raw Terrarium WebP for one tile, or null where the endpoint has no data (ocean, out of range). */
    suspend fun bytes(zoom: Int, x: Int, y: Int): ByteArray? {
        val tile = TileIndex(zoom, x, y)
        val source = sourceFor(zoom)
        return readLocal(source, tile) ?: download(zoom, x, y)?.also { writeLocal(source, tile, it) }
    }

    private suspend fun download(zoom: Int, x: Int, y: Int): ByteArray? = gate.withPermit {
        runCatching {
            val response = http.get(MapterhornEndpoints.tileUrl(zoom, x, y))
            if (response.status.isSuccess()) response.bodyAsBytes() else null
        }
            .getOrNull()
    }

    /** Okio is blocking, so file access goes to IO rather than stalling a compute thread. */
    private suspend fun readLocal(source: TerrainSource, tile: TileIndex): ByteArray? {
        val local = store ?: return null
        return withContext(ioDispatcher) { runCatching { local.readTile(source, tile) }.getOrNull() }
    }

    private suspend fun writeLocal(source: TerrainSource, tile: TileIndex, webpBytes: ByteArray) {
        val local = store ?: return
        // A failed write is a slower next run, never a failed prediction.
        withContext(ioDispatcher) { runCatching { local.writeTile(source, tile, webpBytes) } }
    }

    /** The store splits tiles by tier, and the app's own terrain download writes them the same way. */
    private fun sourceFor(zoom: Int): TerrainSource =
        if (zoom > MapterhornEndpoints.GLOBAL_MAX_ZOOM) TerrainSource.REGIONAL else TerrainSource.GLOBAL

    private companion object {
        /**
         * In-flight requests. HTTP/2 multiplexes these over one connection and each is ~130 ms, so this is about how
         * much of the round-trip latency we hide, not about sockets.
         */
        const val DEFAULT_CONCURRENCY = 24
    }
}
