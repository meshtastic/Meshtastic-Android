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

    private fun gunzip(bytes: ByteArray): ByteArray = GZIPInputStream(bytes.inputStream()).use { it.readBytes() }
}
