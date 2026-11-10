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

import ch.poole.geo.pmtiles.Constants
import ch.poole.geo.pmtiles.Reader
import java.io.FileNotFoundException
import java.net.URI

// The reader reads the archive header on construction, and HttpURLConnection reports a 404 as FileNotFoundException.
actual fun openTerrainTileFetcher(pmtilesUrl: String): TerrainTileFetcher? = try {
    PmtilesTerrainTileFetcher(pmtilesUrl)
} catch (_: FileNotFoundException) {
    null
}

/**
 * Backed by `ch.poole.geo.pmtiles:Reader` (MIT-licensed), the same library the base offline layer uses for Protomaps.
 */
private class PmtilesTerrainTileFetcher(pmtilesUrl: String) : TerrainTileFetcher {

    private val channel = RangeCheckedHttpChannel(URI(pmtilesUrl).toURL())
    private val reader: Reader = Reader(channel)

    override val version: String?
        get() = channel.etag

    override fun fetchTile(zoom: Int, x: Int, y: Int): ByteArray? {
        val raw = reader.getTile(zoom, x, y) ?: return null
        return if (reader.tileCompression == Constants.COMPRESSION_GZIP) {
            gunzipBounded(raw, MAX_DECOMPRESSED_TILE_BYTES)
        } else {
            raw
        }
    }

    override fun close() {
        reader.close()
    }

    private companion object {
        /** A Terrarium tile decodes to at most 256×256×4 bytes; this is a generous multiple, not a tight fit. */
        private const val MAX_DECOMPRESSED_TILE_BYTES = 8 * 1024 * 1024
    }
}
