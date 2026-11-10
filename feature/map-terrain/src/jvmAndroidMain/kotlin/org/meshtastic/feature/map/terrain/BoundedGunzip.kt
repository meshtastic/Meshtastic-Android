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

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.zip.GZIPInputStream

/**
 * Inflates gzip [bytes], throwing [IOException] past [maxBytes] of output. `GZIPInputStream` already rejects data that
 * isn't gzip and a truncated or corrupt stream (its trailer CRC and length are checked at end of input).
 */
fun gunzipBounded(bytes: ByteArray, maxBytes: Int): ByteArray {
    GZIPInputStream(bytes.inputStream()).use { gzip ->
        val buffer = ByteArray(GUNZIP_BUFFER_BYTES)
        val output = ByteArrayOutputStream()
        var total = 0
        while (true) {
            val read = gzip.read(buffer)
            if (read == -1) break
            total += read
            if (total > maxBytes) throw IOException("Decompressed tile exceeds $maxBytes bytes")
            output.write(buffer, 0, read)
        }
        return output.toByteArray()
    }
}

private const val GUNZIP_BUFFER_BYTES = 8192
