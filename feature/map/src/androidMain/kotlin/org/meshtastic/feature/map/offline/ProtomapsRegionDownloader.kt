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
@file:Suppress("MagicNumber") // PMTiles v3 specifies its 127-byte header as fixed byte offsets.

package org.meshtastic.feature.map.offline

import kotlinx.coroutines.withContext
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime
import org.meshtastic.core.common.util.ioDispatcher
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.util.zip.GZIPInputStream
import kotlin.math.PI
import kotlin.math.asinh
import kotlin.math.max
import kotlin.math.min
import kotlin.math.tan
import kotlin.time.Clock

const val REPLICATION_TIME_KEY = "planetiler:osm:osmosisreplicationtime"

data class GeoBounds(val minLon: Double, val minLat: Double, val maxLon: Double, val maxLat: Double)

data class DownloadedPack(val sourceBuild: String, val file: File)

fun interface ProtomapsArchiveResolver {
    fun urlFor(date: LocalDate): String
}

fun interface PmtilesRangeClient {
    suspend fun fetch(url: String, range: LongRange): PmtilesRangeResponse
}

data class PmtilesRangeResponse(val statusCode: Int, val body: ByteArray)

class ProtomapsRegionDownloader(
    private val archiveResolver: ProtomapsArchiveResolver = ProtomapsArchiveResolver { date ->
        "https://build.protomaps.com/${date.compact()}.pmtiles"
    },
    private val rangeClient: PmtilesRangeClient = HttpPmtilesRangeClient,
    private val utcDate: () -> LocalDate = { Clock.System.now().toLocalDateTime(TimeZone.UTC).date },
    private val minZoom: Int = MIN_ZOOM,
    private val maxZoom: Int = MAX_ZOOM,
) {

    suspend fun download(bounds: GeoBounds, destination: File): DownloadedPack {
        val source = resolveNewestSource() ?: throw IllegalStateException("No range-capable Protomaps build found")
        val header = source.header
        require(header.tileType == PmtilesTileType.Mvt) { "Protomaps source is not vector MVT" }

        val root =
            readDirectory(
                source.url,
                header.rootDirectoryOffset,
                header.rootDirectoryLength,
                header.internalCompression,
            )
        val effectiveMinZoom = max(minZoom, header.minZoom)
        val effectiveMaxZoom = min(maxZoom, header.maxZoom)
        require(effectiveMinZoom <= effectiveMaxZoom) { "Source has no requested zoom levels" }

        val requestedTileIds = tileIds(bounds, effectiveMinZoom, effectiveMaxZoom)
        require(requestedTileIds.isNotEmpty() && requestedTileIds.size <= MAX_TILES) {
            "Requested map area is too large"
        }
        val resolved = requestedTileIds.mapNotNull { tileId -> resolveTile(source.url, header, root, tileId) }
        require(resolved.isNotEmpty()) { "Source has no tiles in the requested area" }

        val temporary = File(destination.parentFile, ".${destination.name}.tmp")
        destination.parentFile?.mkdirs()
        temporary.delete()
        var completed = false
        try {
            writeExtractedArchive(
                source = source,
                bounds = bounds,
                minZoom = effectiveMinZoom,
                maxZoom = effectiveMaxZoom,
                tiles = resolved,
                destination = temporary,
            )
            require(PmtilesV3Reader(temporary).header.tileType == PmtilesTileType.Mvt) {
                "Extracted file is not vector MVT"
            }
            Files.move(temporary.toPath(), destination.toPath(), ATOMIC_MOVE, REPLACE_EXISTING)
            completed = true
            return DownloadedPack(source.build, destination)
        } finally {
            if (!completed) temporary.delete()
        }
    }

    private suspend fun resolveNewestSource(): Source? {
        val today = utcDate()
        for (offset in 0 until BUILD_LOOKBACK_DAYS) {
            val day = today.minus(DatePeriod(days = offset))
            sourceFor(day)?.let { return it }
        }
        return null
    }

    private suspend fun sourceFor(day: LocalDate): Source? {
        val url = archiveResolver.urlFor(day)
        val first = 0L
        val last = PmtilesV3Reader.HEADER_SIZE - 1L
        val response = rangeClient.fetch(url, first..last)
        val header = if (response.statusCode == HttpURLConnection.HTTP_PARTIAL) {
            PmtilesV3Reader.parseHeader(exactRangeBody(response, first, last))
        } else {
            null
        }
        return header?.let { Source(url = url, build = day.compact(), header = it) }
    }

    @Suppress("ReturnCount") // A PMTiles leaf traversal has terminal hit and miss states.
    private suspend fun resolveTile(
        sourceUrl: String,
        header: PmtilesHeader,
        root: List<PmtilesEntry>,
        tileId: Long,
    ): ResolvedTile? {
        var entries = root
        var leafOffset = header.rootDirectoryOffset
        var leafLength = header.rootDirectoryLength
        repeat(MAX_DIRECTORY_DEPTH) { depth ->
            if (depth > 0) entries = readDirectory(sourceUrl, leafOffset, leafLength, header.internalCompression)
            val entry = findEntry(entries, tileId) ?: return null
            if (entry.runLength == 0) {
                leafOffset = header.leafDirectoryOffset + entry.offset
                leafLength = entry.length.toLong()
            } else {
                return ResolvedTile(tileId, header.tileDataOffset + entry.offset, entry.length)
            }
        }
        return null
    }

    private suspend fun readDirectory(
        sourceUrl: String,
        offset: Long,
        length: Long,
        compression: PmtilesCompression,
    ): List<PmtilesEntry> {
        require(length > 0) { "Empty PMTiles directory" }
        val body = range(sourceUrl, offset, offset + length - 1)
        return parseDirectory(decompress(body, compression))
    }

    private suspend fun writeExtractedArchive(
        source: Source,
        bounds: GeoBounds,
        minZoom: Int,
        maxZoom: Int,
        tiles: List<ResolvedTile>,
        destination: File,
    ) {
        val payloads = linkedMapOf<Pair<Long, Int>, ByteArray>()
        for (tile in tiles.sortedBy { it.sourceOffset }) {
            val key = tile.sourceOffset to tile.length
            if (key !in payloads) {
                payloads[key] = range(source.url, tile.sourceOffset, tile.sourceOffset + tile.length - 1)
            }
        }

        val payloadOffsets = mutableMapOf<Pair<Long, Int>, Long>()
        var tileDataLength = 0L
        for ((key, payload) in payloads) {
            payloadOffsets[key] = tileDataLength
            tileDataLength += payload.size
        }
        val entries =
            tiles
                .map { tile ->
                    PmtilesEntry(
                        tileId = tile.tileId,
                        offset = checkNotNull(payloadOffsets[tile.sourceOffset to tile.length]),
                        length = tile.length,
                        runLength = 1,
                    )
                }
                .sortedBy { it.tileId }
        val rootDirectory = serializeDirectory(entries)
        require(rootDirectory.size <= MAX_ROOT_DIRECTORY_SIZE) {
            "PMTiles root directory exceeds $MAX_ROOT_DIRECTORY_SIZE bytes"
        }
        val metadata = "{\"$REPLICATION_TIME_KEY\":\"${source.build}\"}".encodeToByteArray()
        val tileDataOffset = PmtilesV3Reader.HEADER_SIZE + rootDirectory.size + metadata.size
        val header =
            buildHeader(
                rootDirectoryLength = rootDirectory.size,
                metadataLength = metadata.size,
                tileDataOffset = tileDataOffset,
                tileDataLength = tileDataLength,
                addressedTileCount = entries.size,
                tileContentsCount = payloads.size,
                bounds = bounds,
                minZoom = minZoom,
                maxZoom = maxZoom,
                tileCompression = source.header.tileCompression,
            )

        withContext(ioDispatcher) {
            FileOutputStream(destination).use { output ->
                output.write(header)
                output.write(rootDirectory)
                output.write(metadata)
                payloads.values.forEach(output::write)
                output.fd.sync()
            }
        }
    }

    private suspend fun range(url: String, first: Long, last: Long): ByteArray {
        require(first >= 0 && last >= first) { "Invalid PMTiles byte range" }
        val response = rangeClient.fetch(url, first..last)
        require(response.statusCode == HttpURLConnection.HTTP_PARTIAL) { "Range request was not honored" }
        return exactRangeBody(response, first, last)
    }

    private fun exactRangeBody(response: PmtilesRangeResponse, first: Long, last: Long): ByteArray {
        val expectedLength = last - first + 1
        require(expectedLength > 0 && response.body.size.toLong() == expectedLength) {
            "Range response was truncated or oversized"
        }
        return response.body
    }

    private data class Source(val url: String, val build: String, val header: PmtilesHeader)

    private data class ResolvedTile(val tileId: Long, val sourceOffset: Long, val length: Int)

    private companion object {
        const val BUILD_LOOKBACK_DAYS = 16
        const val MIN_ZOOM = 0
        const val MAX_ZOOM = 13
        const val MAX_TILES = 600_000
        const val MAX_ROOT_DIRECTORY_SIZE = 16 * 1024
        const val MAX_DIRECTORY_DEPTH = 4
    }
}

object HttpPmtilesRangeClient : PmtilesRangeClient {
    override suspend fun fetch(url: String, range: LongRange): PmtilesRangeResponse = withContext(ioDispatcher) {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "GET"
            connection.setRequestProperty("Range", "bytes=${range.first}-${range.last}")
            connection.setRequestProperty("Accept-Encoding", "identity")
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            PmtilesRangeResponse(status, stream?.use { it.readBytes() } ?: byteArrayOf())
        } finally {
            connection.disconnect()
        }
    }
}

class PmtilesV3Reader(file: File) {
    val header: PmtilesHeader
    val metadata: Map<String, String>

    init {
        val bytes = FileInputStream(file).use { it.readBytes() }
        header = requireNotNull(parseHeader(bytes)) { "Invalid PMTiles v3 header" }
        val metadataEnd = header.metadataOffset + header.metadataLength
        require(metadataEnd <= bytes.size) { "Invalid PMTiles metadata range" }
        metadata = parseMetadata(bytes.copyOfRange(header.metadataOffset.toInt(), metadataEnd.toInt()).decodeToString())
    }

    companion object {
        const val HEADER_SIZE = 127

        fun parseHeader(bytes: ByteArray): PmtilesHeader? {
            if (
                bytes.size < HEADER_SIZE ||
                bytes.copyOfRange(0, 7).decodeToString() != "PMTiles" ||
                bytes[7] != 3.toByte()
            ) {
                return null
            }
            return PmtilesHeader(
                rootDirectoryOffset = bytes.longAt(8),
                rootDirectoryLength = bytes.longAt(16),
                metadataOffset = bytes.longAt(24),
                metadataLength = bytes.longAt(32),
                leafDirectoryOffset = bytes.longAt(40),
                tileDataOffset = bytes.longAt(56),
                internalCompression = PmtilesCompression.from(bytes[97]),
                tileCompression = PmtilesCompression.from(bytes[98]),
                tileType = PmtilesTileType.from(bytes[99]),
                minZoom = bytes[100].toInt() and 0xff,
                maxZoom = bytes[101].toInt() and 0xff,
            )
        }
    }
}

data class PmtilesHeader(
    val rootDirectoryOffset: Long,
    val rootDirectoryLength: Long,
    val metadataOffset: Long,
    val metadataLength: Long,
    val leafDirectoryOffset: Long,
    val tileDataOffset: Long,
    val internalCompression: PmtilesCompression,
    val tileCompression: PmtilesCompression,
    val tileType: PmtilesTileType,
    val minZoom: Int,
    val maxZoom: Int,
)

enum class PmtilesTileType(val value: Byte) {
    Unknown(0),
    Mvt(1),
    Png(2),
    Jpeg(3),
    Webp(4),
    Avif(5),
    ;

    companion object {
        fun from(value: Byte): PmtilesTileType = entries.firstOrNull { it.value == value } ?: Unknown
    }
}

enum class PmtilesCompression(val value: Byte) {
    Unknown(0),
    None(1),
    Gzip(2),
    Brotli(3),
    Zstd(4),
    ;

    companion object {
        fun from(value: Byte): PmtilesCompression = entries.firstOrNull { it.value == value } ?: Unknown
    }
}

private data class PmtilesEntry(val tileId: Long, val offset: Long, val length: Int, val runLength: Int)

private fun parseDirectory(bytes: ByteArray): List<PmtilesEntry> {
    val reader = VarintReader(bytes)
    val count = reader.read().toInt()
    require(count in 1..10_000_000) { "Invalid PMTiles directory" }
    val tileIds = LongArray(count)
    var previousId = 0L
    repeat(count) { index ->
        previousId += reader.read()
        tileIds[index] = previousId
    }
    val runLengths = IntArray(count) { reader.read().toInt() }
    val lengths = IntArray(count) { reader.read().toInt() }
    val offsets = LongArray(count)
    repeat(count) { index ->
        val encoded = reader.read()
        offsets[index] = if (encoded == 0L && index > 0) offsets[index - 1] + lengths[index - 1] else encoded - 1
    }
    return List(count) { index -> PmtilesEntry(tileIds[index], offsets[index], lengths[index], runLengths[index]) }
}

private fun findEntry(entries: List<PmtilesEntry>, tileId: Long): PmtilesEntry? {
    var low = 0
    var high = entries.lastIndex
    while (low <= high) {
        val middle = (low + high) ushr 1
        when {
            tileId > entries[middle].tileId -> low = middle + 1
            tileId < entries[middle].tileId -> high = middle - 1
            else -> return entries[middle]
        }
    }
    return entries.getOrNull(high)?.takeIf { it.runLength == 0 || tileId - it.tileId < it.runLength }
}

private fun serializeDirectory(entries: List<PmtilesEntry>): ByteArray {
    val bytes = ArrayList<Byte>()
    fun put(value: Long) {
        var current = value
        while (current >= 0x80) {
            bytes += ((current and 0x7f) or 0x80).toByte()
            current = current ushr 7
        }
        bytes += current.toByte()
    }
    put(entries.size.toLong())
    var previousId = 0L
    entries.forEach { entry ->
        put(entry.tileId - previousId)
        previousId = entry.tileId
    }
    entries.forEach { entry -> put(entry.runLength.toLong()) }
    entries.forEach { entry -> put(entry.length.toLong()) }
    entries.forEach { entry -> put(entry.offset + 1) }
    return bytes.toByteArray()
}

private fun buildHeader(
    rootDirectoryLength: Int,
    metadataLength: Int,
    tileDataOffset: Int,
    tileDataLength: Long,
    addressedTileCount: Int,
    tileContentsCount: Int,
    bounds: GeoBounds,
    minZoom: Int,
    maxZoom: Int,
    tileCompression: PmtilesCompression,
): ByteArray = ByteBuffer.allocate(PmtilesV3Reader.HEADER_SIZE)
    .order(ByteOrder.LITTLE_ENDIAN)
    .apply {
        put("PMTiles".encodeToByteArray())
        put(3)
        putLong(PmtilesV3Reader.HEADER_SIZE.toLong())
        putLong(rootDirectoryLength.toLong())
        putLong((PmtilesV3Reader.HEADER_SIZE + rootDirectoryLength).toLong())
        putLong(metadataLength.toLong())
        putLong(tileDataOffset.toLong())
        putLong(0)
        putLong(tileDataOffset.toLong())
        putLong(tileDataLength)
        putLong(addressedTileCount.toLong())
        putLong(addressedTileCount.toLong())
        putLong(tileContentsCount.toLong())
        put(0)
        put(PmtilesCompression.None.value)
        put(tileCompression.value)
        put(PmtilesTileType.Mvt.value)
        put(minZoom.toByte())
        put(maxZoom.toByte())
        putInt((bounds.minLon * 10_000_000).toInt())
        putInt((bounds.minLat * 10_000_000).toInt())
        putInt((bounds.maxLon * 10_000_000).toInt())
        putInt((bounds.maxLat * 10_000_000).toInt())
        put(minZoom.toByte())
        putInt(((bounds.minLon + bounds.maxLon) * 5_000_000).toInt())
        putInt(((bounds.minLat + bounds.maxLat) * 5_000_000).toInt())
    }
    .array()

private fun decompress(bytes: ByteArray, compression: PmtilesCompression): ByteArray = when (compression) {
    PmtilesCompression.None -> bytes
    PmtilesCompression.Gzip -> GZIPInputStream(ByteArrayInputStream(bytes)).use { it.readBytes() }
    else -> throw IllegalArgumentException("Unsupported PMTiles compression")
}

private fun tileIds(bounds: GeoBounds, minZoom: Int, maxZoom: Int): List<Long> {
    val ids = ArrayList<Long>()
    for (zoom in minZoom..maxZoom) {
        val (left, top) = tileCoordinates(bounds.minLon, bounds.maxLat, zoom)
        val (right, bottom) = tileCoordinates(bounds.maxLon, bounds.minLat, zoom)
        for (x in min(left, right)..max(left, right)) {
            for (y in min(top, bottom)..max(top, bottom)) {
                ids += zxyToTileId(zoom, x, y)
            }
        }
    }
    return ids
}

private fun tileCoordinates(longitude: Double, latitude: Double, zoom: Int): Pair<Int, Int> {
    val tileCount = 1 shl zoom
    val clampedLatitude = latitude.coerceIn(-85.05112878, 85.05112878)
    val latitudeRadians = clampedLatitude * PI / 180
    val x = (((longitude + 180) / 360) * tileCount).toInt().coerceIn(0, tileCount - 1)
    val y = (((1 - asinh(tan(latitudeRadians)) / PI) / 2) * tileCount).toInt().coerceIn(0, tileCount - 1)
    return x to y
}

private fun zxyToTileId(zoom: Int, x: Int, y: Int): Long {
    var accumulated = 0L
    repeat(zoom) { index -> accumulated += 1L shl (2 * index) }
    var xx = x
    var yy = y
    var distance = 0L
    var size = if (zoom == 0) 0 else 1 shl (zoom - 1)
    while (size > 0) {
        val rx = if ((xx and size) != 0) 1 else 0
        val ry = if ((yy and size) != 0) 1 else 0
        distance += size.toLong() * size * ((3 * rx) xor ry)
        if (ry == 0) {
            if (rx == 1) {
                xx = size - 1 - xx
                yy = size - 1 - yy
            }
            val temporary = xx
            xx = yy
            yy = temporary
        }
        size /= 2
    }
    return accumulated + distance
}

private fun ByteArray.longAt(offset: Int): Long =
    ByteBuffer.wrap(this, offset, Long.SIZE_BYTES).order(ByteOrder.LITTLE_ENDIAN).long

private fun parseMetadata(json: String): Map<String, String> =
    Regex("\\\"([^\\\"]+)\\\"\\s*:\\s*\\\"([^\\\"]*)\\\"").findAll(json).associate {
        it.groupValues[1] to it.groupValues[2]
    }

private fun LocalDate.compact(): String = toString().replace("-", "")

private class VarintReader(private val bytes: ByteArray) {
    private var index = 0

    fun read(): Long {
        var result = 0L
        var shift = 0
        while (index < bytes.size && shift < Long.SIZE_BITS) {
            val value = bytes[index++].toInt() and 0xff
            result = result or ((value and 0x7f).toLong() shl shift)
            if (value and 0x80 == 0) return result
            shift += 7
        }
        throw IllegalArgumentException("Invalid PMTiles varint")
    }
}
