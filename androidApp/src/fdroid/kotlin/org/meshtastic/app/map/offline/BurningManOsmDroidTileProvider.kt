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
@file:Suppress("MagicNumber", "TooManyFunctions")

package org.meshtastic.app.map.offline

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import org.osmdroid.tileprovider.MapTileProviderBase
import org.osmdroid.tileprovider.modules.IFilesystemCache
import org.osmdroid.tileprovider.tilesource.BitmapTileSourceBase
import org.osmdroid.util.MapTileIndex
import java.io.ByteArrayInputStream
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.GZIPInputStream

/** Renders the validated, app-private Burning Man PMTiles vector pack without any network fallback. */
class BurningManOsmDroidTileProvider(file: File) : MapTileProviderBase(LocalTileSource) {
    private val archive = LocalPmtiles(file)

    init {
        setUseDataConnection(false)
    }

    override fun getMapTile(pMapTileIndex: Long): Drawable? = archive
        .tile(
            MapTileIndex.getX(pMapTileIndex),
            MapTileIndex.getY(pMapTileIndex),
            MapTileIndex.getZoom(pMapTileIndex),
        )
        ?.let(::rasterize)
        ?.let { bitmap -> BitmapDrawable(null, bitmap) }

    override fun getMinimumZoomLevel(): Int = archive.minZoom

    override fun getMaximumZoomLevel(): Int = archive.maxZoom

    override fun getTileWriter(): IFilesystemCache? = null

    override fun getQueueSize(): Long = 0

    fun covers(latitude: Double, longitude: Double): Boolean = archive.covers(latitude, longitude)

    private fun rasterize(vectorTile: ByteArray): Bitmap {
        val bitmap = Bitmap.createBitmap(TILE_SIZE, TILE_SIZE, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(PLAYA_COLOR)
        val canvas = Canvas(bitmap)
        val features = decodeVectorTile(vectorTile)
        drawAreaLayer(canvas, features, "landcover", LANDCOVER_COLOR)
        drawAreaLayer(canvas, features, "landuse", LANDUSE_COLOR)
        drawAreaLayer(canvas, features, "water", WATER_COLOR)
        features.filter { it.layer == "roads" }.forEach { drawRoad(bitmap, it) }
        return bitmap
    }

    private fun drawAreaLayer(canvas: Canvas, features: List<VectorFeature>, layer: String, color: Int) {
        val paint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                this.color = color
                style = Paint.Style.FILL
            }
        features
            .filter { it.layer == layer && it.type == GeometryType.Polygon }
            .forEach { feature -> feature.paths.forEach { path -> canvas.drawPath(path.path, paint) } }
    }

    private fun drawRoad(bitmap: Bitmap, feature: VectorFeature) {
        if (feature.type != GeometryType.LineString) return
        val kind = feature.tags["kind"]
        val kindDetail = feature.tags["kind_detail"]
        if (kind in EXCLUDED_PATH_KINDS || (kind == PATH_KIND && kindDetail != PEDESTRIAN_KIND_DETAIL)) return
        val color = if (kind == PATH_KIND) MINOR_ROAD_COLOR else ROAD_COLOR
        val width = if (kind == PATH_KIND) PEDESTRIAN_ROAD_WIDTH else ROAD_WIDTH
        feature.paths.forEach { path ->
            path.points.zipWithNext().forEach { (start, end) -> drawLine(bitmap, start, end, color, width) }
            if (path.closed && path.points.size > 2) {
                drawLine(bitmap, path.points.last(), path.points.first(), color, width)
            }
        }
    }

    private fun drawLine(bitmap: Bitmap, start: VectorPoint, end: VectorPoint, color: Int, width: Float) {
        val dx = end.x - start.x
        val dy = end.y - start.y
        val steps = maxOf(kotlin.math.abs(dx), kotlin.math.abs(dy)).toInt().coerceAtLeast(1)
        val radius = (width / 2f).toInt().coerceAtLeast(1)
        repeat(steps + 1) { index ->
            val progress = index.toFloat() / steps
            fillCircle(bitmap, start.x + dx * progress, start.y + dy * progress, radius, color)
        }
    }

    private fun fillCircle(bitmap: Bitmap, centerX: Float, centerY: Float, radius: Int, color: Int) {
        val x = centerX.toInt()
        val y = centerY.toInt()
        for (offsetY in -radius..radius) {
            for (offsetX in -radius..radius) {
                if (offsetX * offsetX + offsetY * offsetY <= radius * radius) {
                    val pixelX = x + offsetX
                    val pixelY = y + offsetY
                    if (pixelX in 0 until TILE_SIZE && pixelY in 0 until TILE_SIZE) {
                        bitmap.setPixel(pixelX, pixelY, color)
                    }
                }
            }
        }
    }

    private object LocalTileSource : BitmapTileSourceBase("Burning Man local", 0, 15, TILE_SIZE, ".png")

    private companion object {
        const val TILE_SIZE = 256
        const val PATH_KIND = "path"
        const val PEDESTRIAN_KIND_DETAIL = "pedestrian"
        val EXCLUDED_PATH_KINDS = setOf("footway", "cycleway", "track")
        val PLAYA_COLOR = Color.rgb(234, 234, 234)
        val LANDCOVER_COLOR = Color.rgb(228, 221, 202)
        val LANDUSE_COLOR = Color.rgb(238, 228, 196)
        val WATER_COLOR = Color.rgb(144, 198, 231)
        val ROAD_COLOR = Color.rgb(255, 255, 255)
        val MINOR_ROAD_COLOR = Color.rgb(254, 181, 196)
        const val ROAD_WIDTH = 4f
        const val PEDESTRIAN_ROAD_WIDTH = 3f
    }
}

private class LocalPmtiles(private val file: File) {
    private val header: PmtilesHeader
    private val rootDirectory: List<PmtilesDirectoryEntry>

    val minZoom
        get() = header.minZoom

    val maxZoom
        get() = header.maxZoom

    init {
        require(file.isFile) { "Burning Man PMTiles file is missing" }
        header = readHeader(file)
        require(header.tileType == VECTOR_TILE_TYPE) { "Burning Man pack is not vector MVT" }
        rootDirectory = parseDirectory(readRange(file, header.rootDirectoryOffset, header.rootDirectoryLength.toInt()))
    }

    fun covers(latitude: Double, longitude: Double): Boolean =
        longitude in header.minLongitude..header.maxLongitude && latitude in header.minLatitude..header.maxLatitude

    @Suppress("ReturnCount")
    fun tile(x: Int, y: Int, zoom: Int): ByteArray? {
        if (zoom !in header.minZoom..header.maxZoom || x !in 0 until (1 shl zoom) || y !in 0 until (1 shl zoom)) {
            return null
        }
        val entry = findEntry(rootDirectory, zxyToTileId(zoom, x, y)) ?: return null
        if (entry.runLength == 0) return null
        val compressed = readRange(file, header.tileDataOffset + entry.offset, entry.length)
        return when (header.tileCompression) {
            NO_COMPRESSION -> compressed
            GZIP_COMPRESSION -> GZIPInputStream(ByteArrayInputStream(compressed)).use { it.readBytes() }
            else -> null
        }
    }
}

private data class PmtilesHeader(
    val rootDirectoryOffset: Long,
    val rootDirectoryLength: Long,
    val tileDataOffset: Long,
    val tileCompression: Int,
    val tileType: Int,
    val minZoom: Int,
    val maxZoom: Int,
    val minLongitude: Double,
    val minLatitude: Double,
    val maxLongitude: Double,
    val maxLatitude: Double,
)

private data class PmtilesDirectoryEntry(val tileId: Long, val offset: Long, val length: Int, val runLength: Int)

private fun readHeader(file: File): PmtilesHeader {
    val bytes = readRange(file, 0, PMTILES_HEADER_SIZE)
    require(bytes.copyOfRange(0, 7).decodeToString() == "PMTiles" && bytes[7] == PMTILES_VERSION.toByte()) {
        "Invalid PMTiles v3 file"
    }
    val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
    return PmtilesHeader(
        rootDirectoryOffset = buffer.getLong(ROOT_DIRECTORY_OFFSET),
        rootDirectoryLength = buffer.getLong(ROOT_DIRECTORY_LENGTH),
        tileDataOffset = buffer.getLong(TILE_DATA_OFFSET),
        tileCompression = bytes[TILE_COMPRESSION_OFFSET].toInt() and 0xff,
        tileType = bytes[TILE_TYPE_OFFSET].toInt() and 0xff,
        minZoom = bytes[MIN_ZOOM_OFFSET].toInt() and 0xff,
        maxZoom = bytes[MAX_ZOOM_OFFSET].toInt() and 0xff,
        minLongitude = buffer.getInt(MIN_LONGITUDE_OFFSET) / COORDINATE_SCALE,
        minLatitude = buffer.getInt(MIN_LATITUDE_OFFSET) / COORDINATE_SCALE,
        maxLongitude = buffer.getInt(MAX_LONGITUDE_OFFSET) / COORDINATE_SCALE,
        maxLatitude = buffer.getInt(MAX_LATITUDE_OFFSET) / COORDINATE_SCALE,
    )
}

private fun readRange(file: File, offset: Long, length: Int): ByteArray {
    require(offset >= 0 && length >= 0 && offset + length <= file.length()) { "Invalid PMTiles range" }
    return RandomAccessFile(file, "r").use { input ->
        input.seek(offset)
        ByteArray(length).also(input::readFully)
    }
}

private fun parseDirectory(bytes: ByteArray): List<PmtilesDirectoryEntry> {
    val reader = VarintReader(bytes)
    val count = reader.read().toInt()
    require(count in 1..MAX_DIRECTORY_ENTRIES) { "Invalid PMTiles directory" }
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
    return List(count) { index ->
        PmtilesDirectoryEntry(tileIds[index], offsets[index], lengths[index], runLengths[index])
    }
}

private fun findEntry(entries: List<PmtilesDirectoryEntry>, tileId: Long): PmtilesDirectoryEntry? {
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
    return entries.getOrNull(high)?.takeIf { entry -> entry.runLength == 0 || tileId - entry.tileId < entry.runLength }
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

private data class VectorFeature(
    val layer: String,
    val tags: Map<String, String>,
    val type: GeometryType,
    val paths: List<VectorPath>,
)

private data class VectorPoint(val x: Float, val y: Float)

private data class VectorPath(val path: Path, val points: MutableList<VectorPoint>, var closed: Boolean = false)

private enum class GeometryType {
    Unknown,
    Point,
    LineString,
    Polygon,
}

private fun decodeVectorTile(bytes: ByteArray): List<VectorFeature> {
    val reader = WireReader(bytes)
    val features = mutableListOf<VectorFeature>()
    while (!reader.isAtEnd) {
        val tag = reader.readVarint().toInt()
        if (tag ushr 3 == TILE_LAYER_FIELD && tag and WIRE_TYPE_MASK == LENGTH_DELIMITED) {
            features += decodeLayer(reader.readBytes())
        } else {
            reader.skip(tag and WIRE_TYPE_MASK)
        }
    }
    return features
}

private fun decodeLayer(bytes: ByteArray): List<VectorFeature> {
    val reader = WireReader(bytes)
    var name = ""
    var extent = DEFAULT_EXTENT
    val encodedFeatures = mutableListOf<ByteArray>()
    val keys = mutableListOf<String>()
    val values = mutableListOf<String>()
    while (!reader.isAtEnd) {
        val tag = reader.readVarint().toInt()
        when (tag ushr 3) {
            LAYER_NAME_FIELD -> name = reader.readBytes().decodeToString()
            LAYER_FEATURE_FIELD -> encodedFeatures += reader.readBytes()
            LAYER_KEY_FIELD -> keys += reader.readBytes().decodeToString()
            LAYER_VALUE_FIELD -> values += decodeValue(reader.readBytes())
            LAYER_EXTENT_FIELD -> extent = reader.readVarint().toInt()
            else -> reader.skip(tag and WIRE_TYPE_MASK)
        }
    }
    return encodedFeatures.mapNotNull { decodeFeature(name, it, keys, values, extent) }
}

private fun decodeValue(bytes: ByteArray): String {
    val reader = WireReader(bytes)
    while (!reader.isAtEnd) {
        val tag = reader.readVarint().toInt()
        if (tag ushr 3 == VALUE_STRING_FIELD && tag and WIRE_TYPE_MASK == LENGTH_DELIMITED) {
            return reader.readBytes().decodeToString()
        }
        reader.skip(tag and WIRE_TYPE_MASK)
    }
    return ""
}

private fun decodeFeature(
    layer: String,
    bytes: ByteArray,
    keys: List<String>,
    values: List<String>,
    extent: Int,
): VectorFeature? {
    val reader = WireReader(bytes)
    var tags = emptyList<Long>()
    var type = GeometryType.Unknown
    var geometry = byteArrayOf()
    while (!reader.isAtEnd) {
        val tag = reader.readVarint().toInt()
        when (tag ushr 3) {
            FEATURE_TAGS_FIELD -> tags = WireReader(reader.readBytes()).readAllVarints()

            FEATURE_TYPE_FIELD ->
                type = GeometryType.entries.getOrElse(reader.readVarint().toInt()) { GeometryType.Unknown }

            FEATURE_GEOMETRY_FIELD -> geometry = reader.readBytes()

            else -> reader.skip(tag and WIRE_TYPE_MASK)
        }
    }
    if (type == GeometryType.Unknown || geometry.isEmpty()) return null
    return VectorFeature(layer, decodeTags(tags, keys, values), type, decodePaths(geometry, extent))
}

private fun decodeTags(tags: List<Long>, keys: List<String>, values: List<String>): Map<String, String> = tags
    .chunked(2)
    .mapNotNull { pair ->
        if (pair.size == 2) {
            keys.getOrNull(pair[0].toInt())?.let { key -> values.getOrNull(pair[1].toInt())?.let { key to it } }
        } else {
            null
        }
    }
    .toMap()

private fun decodePaths(bytes: ByteArray, extent: Int): List<VectorPath> {
    val reader = WireReader(bytes)
    val paths = mutableListOf<VectorPath>()
    var currentPath: VectorPath? = null
    var x = 0
    var y = 0
    while (!reader.isAtEnd) {
        val commandInteger = reader.readVarint().toInt()
        val command = commandInteger and COMMAND_MASK
        repeat(commandInteger ushr COMMAND_SHIFT) {
            when (command) {
                MOVE_TO -> {
                    x += zigZagDecode(reader.readVarint())
                    y += zigZagDecode(reader.readVarint())
                    val point = VectorPoint(scale(x, extent), scale(y, extent))
                    currentPath = VectorPath(Path().apply { moveTo(point.x, point.y) }, mutableListOf(point))
                    paths += checkNotNull(currentPath)
                }

                LINE_TO -> {
                    x += zigZagDecode(reader.readVarint())
                    y += zigZagDecode(reader.readVarint())
                    val point = VectorPoint(scale(x, extent), scale(y, extent))
                    currentPath?.apply {
                        path.lineTo(point.x, point.y)
                        points += point
                    }
                }

                CLOSE_PATH ->
                    currentPath?.apply {
                        path.close()
                        closed = true
                    }

                else -> return paths
            }
        }
    }
    return paths
}

private fun scale(value: Int, extent: Int): Float = value.toFloat() * TILE_SIZE / extent

private fun zigZagDecode(value: Long): Int = ((value ushr 1) xor -(value and 1)).toInt()

private class WireReader(private val bytes: ByteArray) {
    private var index = 0
    val isAtEnd
        get() = index >= bytes.size

    fun readVarint(): Long {
        var value = 0L
        var shift = 0
        while (index < bytes.size && shift < Long.SIZE_BITS) {
            val next = bytes[index++].toInt() and 0xff
            value = value or ((next and 0x7f).toLong() shl shift)
            if (next and 0x80 == 0) return value
            shift += 7
        }
        throw IllegalArgumentException("Invalid protobuf varint")
    }

    fun readBytes(): ByteArray {
        val length = readVarint().toInt()
        require(length >= 0 && index + length <= bytes.size) { "Invalid protobuf bytes" }
        return bytes.copyOfRange(index, index + length).also { index += length }
    }

    fun readAllVarints(): List<Long> = buildList { while (!isAtEnd) add(readVarint()) }

    fun skip(wireType: Int) {
        when (wireType) {
            VARINT -> readVarint()
            LENGTH_DELIMITED -> readBytes()
            FIXED_64 -> index += Long.SIZE_BYTES
            FIXED_32 -> index += Int.SIZE_BYTES
            else -> throw IllegalArgumentException("Unsupported protobuf wire type")
        }
        require(index <= bytes.size) { "Invalid protobuf field" }
    }
}

private class VarintReader(private val bytes: ByteArray) {
    private var index = 0

    fun read(): Long {
        var value = 0L
        var shift = 0
        while (index < bytes.size && shift < Long.SIZE_BITS) {
            val next = bytes[index++].toInt() and 0xff
            value = value or ((next and 0x7f).toLong() shl shift)
            if (next and 0x80 == 0) return value
            shift += 7
        }
        throw IllegalArgumentException("Invalid PMTiles varint")
    }
}

private const val PMTILES_HEADER_SIZE = 127
private const val PMTILES_VERSION = 3
private const val ROOT_DIRECTORY_OFFSET = 8
private const val ROOT_DIRECTORY_LENGTH = 16
private const val TILE_DATA_OFFSET = 56
private const val TILE_COMPRESSION_OFFSET = 98
private const val TILE_TYPE_OFFSET = 99
private const val MIN_ZOOM_OFFSET = 100
private const val MAX_ZOOM_OFFSET = 101
private const val MIN_LONGITUDE_OFFSET = 102
private const val MIN_LATITUDE_OFFSET = 106
private const val MAX_LONGITUDE_OFFSET = 110
private const val MAX_LATITUDE_OFFSET = 114
private const val VECTOR_TILE_TYPE = 1
private const val NO_COMPRESSION = 1
private const val GZIP_COMPRESSION = 2
private const val MAX_DIRECTORY_ENTRIES = 10_000_000
private const val COORDINATE_SCALE = 10_000_000.0
private const val TILE_LAYER_FIELD = 3
private const val LAYER_NAME_FIELD = 1
private const val LAYER_FEATURE_FIELD = 2
private const val LAYER_KEY_FIELD = 3
private const val LAYER_VALUE_FIELD = 4
private const val LAYER_EXTENT_FIELD = 5
private const val VALUE_STRING_FIELD = 1
private const val FEATURE_TAGS_FIELD = 2
private const val FEATURE_TYPE_FIELD = 3
private const val FEATURE_GEOMETRY_FIELD = 4
private const val DEFAULT_EXTENT = 4096
private const val WIRE_TYPE_MASK = 7
private const val VARINT = 0
private const val FIXED_64 = 1
private const val LENGTH_DELIMITED = 2
private const val FIXED_32 = 5
private const val COMMAND_MASK = 7
private const val COMMAND_SHIFT = 3
private const val MOVE_TO = 1
private const val LINE_TO = 2
private const val CLOSE_PATH = 7
private const val TILE_SIZE = 256f
