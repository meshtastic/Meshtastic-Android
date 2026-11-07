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

import ch.poole.geo.pmtiles.UrlFileChannel
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.nio.ByteBuffer

/**
 * A PMTiles channel over HTTP range requests that only accepts the bytes it asked for. A 200 that ignored the Range
 * header and a 206 whose Content-Range names other bytes both throw [IOException]; an ETag that differs from the first
 * one seen throws [ArchiveChangedException]. A 404 surfaces as `FileNotFoundException`, as with any HttpURLConnection.
 */
class RangeCheckedHttpChannel(private val url: URL) : UrlFileChannel() {

    private var etag: String? = null

    override fun read(dst: ByteBuffer, pos: Long): Int {
        val capacity = dst.capacity()
        val last = pos + capacity - 1
        val connection = url.openConnection() as HttpURLConnection
        connection.instanceFollowRedirects = true
        connection.setRequestProperty("Range", "bytes=$pos-$last")
        connection.inputStream.use { input ->
            if (connection.responseCode != HttpURLConnection.HTTP_PARTIAL) {
                throw IOException("Expected 206 for a range request, got ${connection.responseCode}")
            }
            if (!contentRangeMatches(connection.getHeaderField("Content-Range"), pos, last)) {
                throw IOException("Content-Range does not match the requested bytes")
            }
            checkEtag(connection.getHeaderField("ETag"))
            dst.rewind()
            var offset = 0
            while (offset < capacity) {
                val count = input.read(dst.array(), offset, capacity - offset)
                if (count == -1) break
                offset += count
            }
            return offset
        }
    }

    private fun checkEtag(received: String?) {
        if (received == null) return
        val expected = etag
        if (expected == null) {
            etag = received
        } else if (expected != received) {
            throw ArchiveChangedException()
        }
    }
}

private val CONTENT_RANGE = Regex("""bytes (\d+)-(\d+)/(\d+|\*)""")

/**
 * Whether a Content-Range [header] covers exactly [first]..[last], or [first] to the end of a file that ends before
 * [last].
 */
internal fun contentRangeMatches(header: String?, first: Long, last: Long): Boolean {
    val match = header?.trim()?.let(CONTENT_RANGE::matchEntire) ?: return false
    val (start, end, total) = match.destructured
    val endByte = end.toLong()
    val totalBytes = total.toLongOrNull()
    return start.toLong() == first &&
        (endByte == last || (totalBytes != null && endByte == totalBytes - 1 && endByte < last))
}
