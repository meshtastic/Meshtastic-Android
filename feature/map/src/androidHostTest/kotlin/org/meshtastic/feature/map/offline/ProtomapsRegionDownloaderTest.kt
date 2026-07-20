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
package org.meshtastic.feature.map.offline

import kotlinx.coroutines.test.runTest
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ProtomapsRegionDownloaderTest {

    @Test
    fun `downloads newest range-capable UTC build and writes MVT PMTiles`() = runTest {
        val today = LocalDate(2026, 9, 2)
        val newestBuild = today.minus(DatePeriod(days = 1))
        val resolver = ProtomapsArchiveResolver { date -> "https://example.test/$date.pmtiles" }
        val fakeClient =
            FakeRangeClient(
                mapOf(
                    resolver.urlFor(today) to FakeArchive(statusCode = 200, bytes = vectorPmtiles()),
                    resolver.urlFor(newestBuild) to FakeArchive(statusCode = 206, bytes = vectorPmtiles()),
                ),
            )
        val directory = Files.createTempDirectory("protomaps-region-test").toFile()
        val destination = File(directory, "region.pmtiles")

        try {
            val pack =
                ProtomapsRegionDownloader(archiveResolver = resolver, rangeClient = fakeClient, utcDate = { today })
                    .download(
                        bounds = GeoBounds(minLon = -119.21, minLat = 40.78, maxLon = -119.20, maxLat = 40.79),
                        destination = destination,
                    )

            assertEquals("20260901", pack.sourceBuild)
            assertEquals(destination, pack.file)
            assertTrue(destination.isFile)
            assertEquals(PmtilesTileType.Mvt, PmtilesV3Reader(destination).header.tileType)
            assertEquals("20260901", PmtilesV3Reader(destination).metadata[REPLICATION_TIME_KEY])
            assertEquals(listOf(resolver.urlFor(today), resolver.urlFor(newestBuild)), fakeClient.probedUrls)
        } finally {
            directory.deleteRecursively()
        }
    }

    private class FakeRangeClient(private val archives: Map<String, FakeArchive>) : PmtilesRangeClient {
        val probedUrls = mutableListOf<String>()

        override suspend fun fetch(url: String, range: LongRange): PmtilesRangeResponse {
            val archive = checkNotNull(archives[url])
            if (range.first == 0L && range.last == PmtilesV3Reader.HEADER_SIZE - 1L) probedUrls += url
            val start = range.first.toInt()
            val end = (range.last + 1).coerceAtMost(archive.bytes.size.toLong()).toInt()
            return PmtilesRangeResponse(archive.statusCode, archive.bytes.copyOfRange(start, end))
        }
    }

    private data class FakeArchive(val statusCode: Int, val bytes: ByteArray)

    private fun vectorPmtiles(): ByteArray {
        val tile = byteArrayOf(0x1A, 0x00)
        val directory = byteArrayOf(1, 0, 1, tile.size.toByte(), 1)
        val metadata = "{}".encodeToByteArray()
        val header = ByteBuffer.allocate(PmtilesV3Reader.HEADER_SIZE).order(ByteOrder.LITTLE_ENDIAN)
        header.put("PMTiles".encodeToByteArray())
        header.put(3)
        header.putLong(PmtilesV3Reader.HEADER_SIZE.toLong())
        header.putLong(directory.size.toLong())
        header.putLong((PmtilesV3Reader.HEADER_SIZE + directory.size).toLong())
        header.putLong(metadata.size.toLong())
        header.putLong((PmtilesV3Reader.HEADER_SIZE + directory.size + metadata.size).toLong())
        header.putLong(0)
        header.putLong((PmtilesV3Reader.HEADER_SIZE + directory.size + metadata.size).toLong())
        header.putLong(tile.size.toLong())
        header.putLong(1)
        header.putLong(1)
        header.putLong(1)
        header.put(1)
        header.put(1)
        header.put(1)
        header.put(PmtilesTileType.Mvt.value)

        return header.array() + directory + metadata + tile
    }
}
