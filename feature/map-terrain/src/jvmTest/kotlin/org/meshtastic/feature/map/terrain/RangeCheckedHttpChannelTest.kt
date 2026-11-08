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

import com.sun.net.httpserver.HttpServer
import java.io.ByteArrayOutputStream
import java.io.FileNotFoundException
import java.io.IOException
import java.net.InetSocketAddress
import java.net.URI
import java.nio.ByteBuffer
import java.util.zip.GZIPOutputStream
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RangeCheckedHttpChannelTest {

    private val body = ByteArray(64) { it.toByte() }

    /** How the test server answers a range request. */
    private enum class Mode {
        HONEST,
        IGNORES_RANGE,
        WRONG_RANGE,
    }

    private var mode = Mode.HONEST
    private var etag: String? = "\"v1\""

    /** When set, the server answers every request with this status and no body. */
    private var status: Int? = null

    private val server =
        HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            createContext("/archive.pmtiles") { exchange ->
                val (first, last) =
                    exchange.requestHeaders.getFirst("Range")!!.removePrefix("bytes=").split('-').map { it.toInt() }
                etag?.let { exchange.responseHeaders.add("ETag", it) }
                status?.let {
                    exchange.sendResponseHeaders(it, -1)
                    exchange.close()
                    return@createContext
                }
                when (mode) {
                    Mode.IGNORES_RANGE -> {
                        exchange.sendResponseHeaders(200, body.size.toLong())
                        exchange.responseBody.use { it.write(body) }
                    }

                    Mode.HONEST,
                    Mode.WRONG_RANGE,
                    -> {
                        val claimedFirst = if (mode == Mode.WRONG_RANGE) first + 1 else first
                        exchange.responseHeaders.add("Content-Range", "bytes $claimedFirst-$last/${body.size}")
                        val slice = body.copyOfRange(first, last + 1)
                        exchange.sendResponseHeaders(206, slice.size.toLong())
                        exchange.responseBody.use { it.write(slice) }
                    }
                }
            }
            start()
        }

    private val channel =
        RangeCheckedHttpChannel(URI("http://127.0.0.1:${server.address.port}/archive.pmtiles").toURL())

    @AfterTest fun stop() = server.stop(0)

    private fun read(pos: Long, length: Int): ByteArray {
        val buffer = ByteBuffer.allocate(length)
        val count = channel.read(buffer, pos)
        return buffer.array().copyOf(count)
    }

    @Test
    fun `an honest partial response returns the requested bytes`() {
        assertContentEquals(body.copyOfRange(10, 20), read(pos = 10, length = 10))
    }

    @Test
    fun `a full response that ignored the range is rejected`() {
        mode = Mode.IGNORES_RANGE
        assertFailsWith<IOException> { read(pos = 10, length = 10) }
    }

    @Test
    fun `a partial response for other bytes is rejected`() {
        mode = Mode.WRONG_RANGE
        assertFailsWith<IOException> { read(pos = 10, length = 10) }
    }

    @Test
    fun `a changed etag between reads is an archive change`() {
        read(pos = 0, length = 8)
        etag = "\"v2\""
        assertFailsWith<ArchiveChangedException> { read(pos = 8, length = 8) }
    }

    @Test
    fun `an etag that goes missing after the first read is an archive change`() {
        read(pos = 0, length = 8)
        etag = null
        assertFailsWith<ArchiveChangedException> { read(pos = 8, length = 8) }
    }

    @Test
    fun `a range that is no longer satisfiable is an archive change`() {
        read(pos = 0, length = 8)
        status = 416
        assertFailsWith<ArchiveChangedException> { read(pos = 8, length = 8) }
    }

    @Test
    fun `only a 404 reads as a missing archive`() {
        status = 404
        assertFailsWith<FileNotFoundException> { read(pos = 0, length = 8) }
        status = 410
        val gone = assertFailsWith<IOException> { read(pos = 0, length = 8) }
        assertFalse(gone is FileNotFoundException)
    }

    @Test
    fun `content range rejects numbers too large to parse`() {
        assertFalse(contentRangeMatches("bytes 99999999999999999999-63/64", first = 60, last = 63))
    }

    @Test
    fun `content range accepts a short read only at the end of the file`() {
        assertTrue(contentRangeMatches("bytes 60-63/64", first = 60, last = 70))
        assertFalse(contentRangeMatches("bytes 60-62/64", first = 60, last = 70))
        assertFalse(contentRangeMatches("bytes 60-63/*", first = 60, last = 70))
        assertFalse(contentRangeMatches(null, first = 60, last = 70))
    }

    private fun gzip(bytes: ByteArray): ByteArray =
        ByteArrayOutputStream().also { out -> GZIPOutputStream(out).use { it.write(bytes) } }.toByteArray()

    @Test
    fun `gunzip rejects truncated data and output past the limit`() {
        val compressed = gzip(ByteArray(1_000))
        assertEquals(1_000, gunzipBounded(compressed, maxBytes = 1_000).size)
        assertFailsWith<IOException> { gunzipBounded(compressed.copyOf(compressed.size - 4), maxBytes = 1_000) }
        assertFailsWith<IOException> { gunzipBounded(compressed, maxBytes = 999) }
        assertFailsWith<IOException> { gunzipBounded(ByteArray(16) { 7 }, maxBytes = 1_000) }
    }
}
