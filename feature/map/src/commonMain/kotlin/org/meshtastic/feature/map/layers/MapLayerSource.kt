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
package org.meshtastic.feature.map.layers

import org.meshtastic.core.common.util.BYTES_PER_MEGABYTE

/**
 * Largest stored GeoJSON or coverage layer that is read back.
 *
 * The GeoJSON parser refuses anything larger, so reading more only fills the heap with bytes it will reject.
 */
const val MAX_GEOJSON_LAYER_MB = 10

/** Largest KML or KMZ source read back. A KMZ carries its images, so it gets the room a shared map file is allowed. */
const val MAX_KML_LAYER_MB = 50

/** Largest source read for a layer of this type, in the megabytes a user is told about. */
fun LayerType.maxSourceMegabytes(): Int = when (this) {
    LayerType.KML -> MAX_KML_LAYER_MB

    LayerType.GEOJSON,
    LayerType.COVERAGE,
    -> MAX_GEOJSON_LAYER_MB
}

fun LayerType.maxSourceBytes(): Long = (maxSourceMegabytes() * BYTES_PER_MEGABYTE).toLong()

/** Why a layer was refused at import or left undrawn. */
enum class MapLayerRefusal {
    /** Over [maxSourceBytes] for its type. */
    TOO_LARGE,

    /** A `.json` that is not GeoJSON. */
    NOT_MAP_DATA,

    /** Parsed, but more vertices than the renderer has room to draw. */
    TOO_DETAILED,
}

/** One refused or undrawn layer. [atImport] separates a file the user just picked from a stored layer. */
class MapLayerProblem(
    val layerName: String,
    val layerType: LayerType,
    val refusal: MapLayerRefusal,
    val atImport: Boolean,
)

/** The `type` values RFC 7946 allows at the root of a GeoJSON document. */
private val GEOJSON_ROOT_TYPES =
    setOf(
        "FeatureCollection",
        "Feature",
        "Point",
        "MultiPoint",
        "LineString",
        "MultiLineString",
        "Polygon",
        "MultiPolygon",
        "GeometryCollection",
    )

/** Whether these bytes are a JSON object whose root `type` names a GeoJSON object. */
fun ByteArray.looksLikeGeoJson(): Boolean = jsonRootType() in GEOJSON_ROOT_TYPES

private const val BYTE_MASK = 0xFF
private const val UTF8_BOM_SIZE = 3
private const val UTF8_BOM_FIRST = 0xEF
private const val TYPE_KEY = "type"

// No GeoJSON type name is longer; a longer value is not one, and is not worth decoding.
private const val MAX_TYPE_VALUE_BYTES = 32

/**
 * The string value of the root object's `type` member, or null when there is none.
 *
 * Scans the bytes in place rather than parsing: any `.json` file can be picked as a layer, and building a tree to learn
 * that one is a package manifest costs several times its size in heap. Structural characters are ASCII, which never
 * occurs inside a multi-byte UTF-8 sequence, so the scan needs no decoding.
 */
@Suppress("CyclomaticComplexMethod", "ReturnCount", "NestedBlockDepth")
internal fun ByteArray.jsonRootType(): String? {
    var i = skipWhitespace(if (size >= UTF8_BOM_SIZE && charAt(0).code == UTF8_BOM_FIRST) UTF8_BOM_SIZE else 0)
    if (i >= size || charAt(i) != '{') return null
    i++
    var depth = 1
    var expectKey = true
    while (i < size) {
        when (charAt(i)) {
            '"' -> {
                val end = stringEnd(i)
                if (end < 0) return null
                if (depth == 1 && expectKey) {
                    val valueStart = skipWhitespace(skipWhitespace(end + 1) + 1)
                    if (isTypeKey(i, end)) return stringValueAt(valueStart)
                    expectKey = false
                    i = valueStart
                    continue
                }
                i = end
            }

            '{',
            '[',
            -> depth++

            '}',
            ']',
            -> if (--depth == 0) return null

            ',' -> if (depth == 1) expectKey = true
        }
        i++
    }
    return null
}

private fun ByteArray.charAt(index: Int): Char = (this[index].toInt() and BYTE_MASK).toChar()

private fun ByteArray.skipWhitespace(from: Int): Int {
    var i = from
    while (i < size && charAt(i).isWhitespace()) i++
    return i
}

/** Index of the quote closing the string that opens at [open], or -1 when it never closes. */
private fun ByteArray.stringEnd(open: Int): Int {
    var i = open + 1
    while (i < size) {
        when (charAt(i)) {
            '\\' -> i++
            '"' -> return i
        }
        i++
    }
    return -1
}

private fun ByteArray.isTypeKey(open: Int, close: Int): Boolean =
    close - open - 1 == TYPE_KEY.length && TYPE_KEY.indices.all { charAt(open + 1 + it) == TYPE_KEY[it] }

private fun ByteArray.stringValueAt(open: Int): String? {
    if (open >= size || charAt(open) != '"') return null
    val close = stringEnd(open)
    return if (close < 0 || close - open - 1 > MAX_TYPE_VALUE_BYTES) null else decodeToString(open + 1, close)
}
