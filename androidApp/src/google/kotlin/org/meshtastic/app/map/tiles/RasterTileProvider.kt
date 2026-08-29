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
package org.meshtastic.app.map.tiles

import co.touchlab.kermit.Logger
import com.google.android.gms.maps.model.Tile
import com.google.android.gms.maps.model.TileProvider
import okhttp3.OkHttpClient
import okhttp3.Request
import org.meshtastic.feature.map.tiles.RasterTileSpec
import org.meshtastic.feature.map.tiles.tileUrl
import java.io.IOException

private val logger = Logger.withTag("RasterTileProvider")

/**
 * Draws a [RasterTileSpec] on the Google map, fetching its tiles through a caching HTTP client.
 *
 * Google's own `UrlTileProvider` opens a bare stream per tile and keeps nothing, so panning re-downloads tiles that
 * were on screen a moment ago and the map blanks out behind the requests (#2714). Going through OkHttp gives the tiles
 * a disk cache that honours what the tile server says about caching, which is what the MapLibre map gets from its
 * renderer for free.
 */
class RasterTileProvider(private val spec: RasterTileSpec, private val client: OkHttpClient) : TileProvider {

    override fun getTile(x: Int, y: Int, zoom: Int): Tile? {
        // NO_TILE means "there is definitively nothing here" and is cached as such; a zoom outside the source's range
        // is exactly that, whereas a failed fetch below is not and must stay retryable.
        val url = spec.tileUrl(x, y, zoom) ?: return TileProvider.NO_TILE
        return fetch(url)
    }

    private fun fetch(url: String): Tile? = try {
        client.newCall(Request.Builder().url(url).build()).execute().use { response ->
            if (response.isSuccessful) {
                response.body.bytes().takeIf { it.isNotEmpty() }?.let { Tile(spec.tileSize, spec.tileSize, it) }
            } else {
                logger.d { "Tile server answered ${response.code}" }
                null
            }
        }
    } catch (e: IOException) {
        // Offline, or the tile host is down. Null rather than NO_TILE, so the map asks again.
        logger.d(e) { "Tile fetch failed" }
        null
    } catch (e: IllegalArgumentException) {
        // OkHttp rejected the URL the template produced. The template is the user's, so this is not ours to crash
        // on — but it will not fix itself either, so refuse the tile rather than retrying forever.
        logger.w(e) { "Tile url rejected" }
        TileProvider.NO_TILE
    }
}
