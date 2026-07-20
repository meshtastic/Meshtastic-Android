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

    @Test
    fun `retains z15 streets within Burning Man pack limits`() = runTest {
        val today = LocalDate(2026, 9, 2)
        val resolver = ProtomapsArchiveResolver { date -> "https://example.test/$date.pmtiles" }
        val fakeClient =
            FakeRangeClient(
                mapOf(
                    resolver.urlFor(today) to
                        FakeArchive(statusCode = 206, bytes = vectorPmtiles(maxZoom = 15, runLength = Int.MAX_VALUE)),
                ),
            )
        val directory = Files.createTempDirectory("protomaps-region-test").toFile()
        val destination = File(directory, "region.pmtiles")

        try {
            ProtomapsRegionDownloader(
                archiveResolver = resolver,
                rangeClient = fakeClient,
                utcDate = { today },
                maxZoom = 15,
            )
                .download(bounds = BURNING_MAN_BOUNDS, destination = destination)

            val header = PmtilesV3Reader(destination).header
            assertEquals(15, header.maxZoom)
            assertTrue(header.rootDirectoryLength <= MAX_ROOT_DIRECTORY_BYTES)
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun `keeps default bounded extraction at z13`() = runTest {
        val today = LocalDate(2026, 9, 2)
        val resolver = ProtomapsArchiveResolver { date -> "https://example.test/$date.pmtiles" }
        val fakeClient =
            FakeRangeClient(
                mapOf(
                    resolver.urlFor(today) to
                        FakeArchive(statusCode = 206, bytes = vectorPmtiles(maxZoom = 15, runLength = Int.MAX_VALUE)),
                ),
            )
        val directory = Files.createTempDirectory("protomaps-region-test").toFile()
        val destination = File(directory, "region.pmtiles")

        try {
            ProtomapsRegionDownloader(archiveResolver = resolver, rangeClient = fakeClient, utcDate = { today })
                .download(bounds = BURNING_MAN_BOUNDS, destination = destination)

            assertEquals(13, PmtilesV3Reader(destination).header.maxZoom)
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun `rejects a region whose root directory exceeds the PMTiles limit`() = runTest {
        val today = LocalDate(2026, 9, 2)
        val resolver = ProtomapsArchiveResolver { date -> "https://example.test/$date.pmtiles" }
        val fakeClient =
            FakeRangeClient(
                mapOf(
                    resolver.urlFor(today) to FakeArchive(statusCode = 206, bytes = vectorPmtiles(runLength = 100_000_000)),
                ),
            )
        val directory = Files.createTempDirectory("protomaps-region-test").toFile()
        val destination = File(directory, "region.pmtiles")

        try {
            val failure =
                runCatching {
                    ProtomapsRegionDownloader(archiveResolver = resolver, rangeClient = fakeClient, utcDate = { today })
                        .download(
                            bounds = GeoBounds(minLon = -3.0, minLat = -3.0, maxLon = 3.0, maxLat = 3.0),
                            destination = destination,
                        )
                }.exceptionOrNull()

            assertTrue(checkNotNull(failure).message?.contains("root directory") == true)
            assertTrue(!destination.exists())
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun `rejects truncated range responses and removes temporary file`() = runTest {
        val today = LocalDate(2026, 9, 2)
        val resolver = ProtomapsArchiveResolver { date -> "https://example.test/$date.pmtiles" }
        val fakeClient =
            FakeRangeClient(
                archives = mapOf(resolver.urlFor(today) to FakeArchive(statusCode = 206, bytes = vectorPmtiles())),
                truncateTilePayload = true,
            )
        val directory = Files.createTempDirectory("protomaps-region-test").toFile()
        val destination = File(directory, "region.pmtiles")

        try {
            val failure =
                runCatching {
                    ProtomapsRegionDownloader(archiveResolver = resolver, rangeClient = fakeClient, utcDate = { today })
                        .download(
                            bounds = GeoBounds(minLon = -119.21, minLat = 40.78, maxLon = -119.20, maxLat = 40.79),
                            destination = destination,
                        )
                }.exceptionOrNull()

            assertTrue(checkNotNull(failure).message?.contains("truncated") == true)
            assertTrue(!destination.exists())
        } finally {
            directory.deleteRecursively()
        }
    }

    private class FakeRangeClient(
        private val archives: Map<String, FakeArchive>,
        private val truncateTilePayload: Boolean = false,
    ) : PmtilesRangeClient {
        val probedUrls = mutableListOf<String>()

        override suspend fun fetch(url: String, range: LongRange): PmtilesRangeResponse {
            val archive = checkNotNull(archives[url])
            if (range.first == 0L && range.last == PmtilesV3Reader.HEADER_SIZE - 1L) probedUrls += url
            val start = range.first.toInt()
            val end = (range.last + 1).coerceAtMost(archive.bytes.size.toLong()).toInt()
            val body = archive.bytes.copyOfRange(start, end)
            val result =
                if (truncateTilePayload && range.first >= TILE_DATA_OFFSET) body.dropLast(1).toByteArray() else body
            return PmtilesRangeResponse(archive.statusCode, result)
        }
    }

    private data class FakeArchive(val statusCode: Int, val bytes: ByteArray)

    private fun vectorPmtiles(maxZoom: Int = 13, runLength: Int = 1): ByteArray {
        val tile = byteArrayOf(0x1A, 0x00)
        val directory = byteArrayOf(1, 0) + varint(runLength) + byteArrayOf(tile.size.toByte(), 1)
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
        header.put(0)
        header.put(maxZoom.toByte())

        return header.array() + directory + metadata + tile
    }

    private fun varint(value: Int): ByteArray {
        var remainder = value
        val result = mutableListOf<Byte>()
        do {
            var next = remainder and 0x7f
            remainder = remainder ushr 7
            if (remainder > 0) next = next or 0x80
            result += next.toByte()
        } while (remainder > 0)
        return result.toByteArray()
    }

    private companion object {
        const val TILE_DATA_OFFSET = 134L
        const val MAX_ROOT_DIRECTORY_BYTES = 16 * 1024L
        val BURNING_MAN_BOUNDS = GeoBounds(minLon = -119.287957, minLat = 40.722536, maxLon = -119.128520, maxLat = 40.843420)
    }
}
