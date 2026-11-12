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
package org.meshtastic.feature.xr

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsBytes
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.meshtastic.feature.map.terrain.ElevationTile
import org.meshtastic.feature.map.terrain.GeoBounds
import org.meshtastic.feature.map.terrain.MapterhornEndpoints
import org.meshtastic.feature.map.terrain.TerrainTileMath
import org.meshtastic.feature.map.terrain.TileIndex
import org.meshtastic.feature.map.terrain.decodeTerrariumTile
import org.meshtastic.feature.map.terrain.spatial.ElevationTileSource

/**
 * Elevation for the spatial map straight from Mapterhorn's XYZ endpoint, a few recent tiles kept in memory. A 404 means
 * no data there (ocean); any other failure throws, so a throttled request never turns a mountain into sea level.
 */
internal class MapterhornElevationSource(private val http: HttpClient) : ElevationTileSource {
    private val lock = Mutex()
    private val cache =
        object : LinkedHashMap<TileIndex, ElevationTile?>(CACHE_TILES, LOAD_FACTOR, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<TileIndex, ElevationTile?>?) =
                size > CACHE_TILES
        }

    override suspend fun elevationTile(z: Int, x: Int, y: Int): ElevationTile? {
        val key = TileIndex(z, x, y)
        lock.withLock { if (cache.containsKey(key)) return cache[key] }
        val response = http.get(MapterhornEndpoints.tileUrl(z, x, y))
        val tile =
            when {
                response.status.isSuccess() -> decodeTerrariumTile(response.bodyAsBytes())
                response.status == HttpStatusCode.NotFound -> null
                else -> error("terrain tile $z/$x/$y: ${response.status}")
            }
        lock.withLock { cache[key] = tile }
        return tile
    }

    override suspend fun coverage(z: Int, x: Int, y: Int): GeoBounds {
        val northwest = TerrainTileMath.lonLatAt(z, x.toDouble(), y.toDouble())
        val southeast = TerrainTileMath.lonLatAt(z, x + 1.0, y + 1.0)
        return GeoBounds(
            south = southeast.latitude,
            west = northwest.longitude,
            north = northwest.latitude,
            east = southeast.longitude,
        )
    }

    override suspend fun finestDataZoom(): Int = DATA_ZOOM

    private companion object {
        const val CACHE_TILES = 48
        const val LOAD_FACTOR = 0.75f

        /** The planner samples no finer than two zooms past this, which keeps a view to a few dozen tiles. */
        const val DATA_ZOOM = 13
    }
}
