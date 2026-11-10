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
import java.io.FileNotFoundException
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.nio.ByteBuffer

/**
 * A PMTiles channel over HTTP range requests that only accepts the bytes it asked for. A 200 that ignored the Range
 * header and a 206 whose Content-Range names other bytes both throw [IOException]. An ETag that differs from the first
 * one seen, or goes missing after it, and a 416 for a range that used to exist throw [ArchiveChangedException]. Only a
 * 404 throws [FileNotFoundException].
 */
class RangeCheckedHttpChannel(private val url: URL) : UrlFileChannel() {

    /** The ETag of the first response, which every later response must match. */
    var etag: String? = null
        private set

    override fun read(dst: ByteBuffer, pos: Long): Int {
        val capacity = dst.capacity()
        val last = pos + capacity - 1
        val connection = url.openConnection() as HttpURLConnection
        connection.instanceFollowRedirects = true
        connection.connectTimeout = CONNECT_TIMEOUT_MS
        connection.readTimeout = READ_TIMEOUT_MS
        connection.setRequestProperty("Range", "bytes=$pos-$last")
        try {
            checkResponse(connection, pos, last)
        } catch (e: IOException) {
            connection.disconnect()
            throw e
        }
        connection.inputStream.use { input ->
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

    private fun checkResponse(connection: HttpURLConnection, pos: Long, last: Long) {
        statusError(connection.responseCode)?.let { throw it }
        if (!contentRangeMatches(connection.getHeaderField("Content-Range"), pos, last)) {
            throw IOException("Content-Range does not match the requested bytes")
        }
        checkEtag(connection.getHeaderField("ETag"))
    }

    private fun statusError(code: Int): IOException? = when (code) {
        HttpURLConnection.HTTP_PARTIAL -> null
        HttpURLConnection.HTTP_NOT_FOUND -> FileNotFoundException(url.toString())
        HTTP_RANGE_NOT_SATISFIABLE -> ArchiveChangedException()
        else -> IOException("Expected 206 for a range request, got $code")
    }

    private fun checkEtag(received: String?) {
        val expected = etag
        if (expected == null) {
            etag = received
        } else if (expected != received) {
            throw ArchiveChangedException()
        }
    }

    private companion object {
        const val HTTP_RANGE_NOT_SATISFIABLE = 416
        const val CONNECT_TIMEOUT_MS = 15_000
        const val READ_TIMEOUT_MS = 30_000
    }
}

private val CONTENT_RANGE = Regex("""bytes (\d+)-(\d+)/(\d+|\*)""")

/**
 * Whether a Content-Range [header] covers exactly [first]..[last], or [first] to the end of a file that ends before
 * [last].
 */
internal fun contentRangeMatches(header: String?, first: Long, last: Long): Boolean {
    val (start, end, total) = header?.trim()?.let(CONTENT_RANGE::matchEntire)?.destructured ?: return false
    val endByte = end.toLongOrNull()
    val totalBytes = total.toLongOrNull()
    return start.toLongOrNull() == first &&
        endByte != null &&
        (endByte == last || (totalBytes != null && endByte == totalBytes - 1 && endByte < last))
}
