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
import ch.poole.geo.pmtiles.HttpUrlConnectionChannel
import ch.poole.geo.pmtiles.Reader
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.URL
import java.util.zip.GZIPInputStream

actual class TerrainTileFetcher actual constructor(pmtilesUrl: String) : AutoCloseable {

    private val reader: Reader = Reader(HttpUrlConnectionChannel(URL(pmtilesUrl)))

    actual fun fetchTile(zoom: Int, x: Int, y: Int): ByteArray? {
        val raw = reader.getTile(zoom, x, y) ?: return null
        return if (reader.tileCompression == Constants.COMPRESSION_GZIP) gunzip(raw) else raw
    }

    actual override fun close() {
        reader.close()
    }

    /**
     * Bounded, not a bare `GZIPInputStream(...).readBytes()`: `download.mapterhorn.com` is a fixed first-party URL, but
     * a compromised or MITM'd response is still a real defense-in-depth gap — an unbounded gunzip is a classic zip-bomb
     * vector. A Terrarium tile decodes to at most 256×256×4 bytes (~256KB); [MAX_DECOMPRESSED_TILE_BYTES] is a generous
     * multiple of that, not a tight fit.
     */
    private fun gunzip(bytes: ByteArray): ByteArray {
        GZIPInputStream(bytes.inputStream()).use { gzip ->
            val buffer = ByteArray(GUNZIP_BUFFER_BYTES)
            val output = ByteArrayOutputStream()
            var totalRead = 0
            while (true) {
                val read = gzip.read(buffer)
                if (read == -1) break
                totalRead += read
                if (totalRead > MAX_DECOMPRESSED_TILE_BYTES) {
                    throw IOException("Decompressed terrain tile exceeds $MAX_DECOMPRESSED_TILE_BYTES bytes")
                }
                output.write(buffer, 0, read)
            }
            return output.toByteArray()
        }
    }

    private companion object {
        private const val MAX_DECOMPRESSED_TILE_BYTES = 8 * 1024 * 1024
        private const val GUNZIP_BUFFER_BYTES = 8192
    }
}
