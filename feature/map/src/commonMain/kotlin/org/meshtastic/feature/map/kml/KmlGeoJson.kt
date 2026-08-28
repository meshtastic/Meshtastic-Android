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
package org.meshtastic.feature.map.kml

import kotlin.math.roundToLong

/** Building the GeoJSON a parsed KML becomes: geometry, simplestyle properties, and the escaping both need. */
internal fun pointGeometry(position: String) = KmlGeometry("Point", position, isPolygonal = false)

internal fun lineGeometry(positions: List<String>) =
    KmlGeometry("LineString", positions.joinToString(",", prefix = "[", postfix = "]"), isPolygonal = false)

internal fun polygonGeometry(ring: List<String>): KmlGeometry {
    // GeoJSON requires a closed ring; KML usually closes its own, but not always.
    val closed = if (ring.first() == ring.last()) ring else ring + ring.first()
    return KmlGeometry(
        type = "Polygon",
        coordinates = closed.joinToString(",", prefix = "[[", postfix = "]]"),
        isPolygonal = true,
    )
}

/**
 * KML coordinates are whitespace-separated `lon,lat[,alt]` tuples; GeoJSON wants `[lon, lat]` pairs.
 *
 * Altitude is dropped rather than carried: nothing on either map reads it, and a third ordinate would make every
 * downstream bounding-box calculation handle a case it never needs to.
 */
internal fun parseCoordinates(raw: String): List<String>? {
    val positions =
        raw.trim().split(WHITESPACE).mapNotNull { tuple ->
            val parts = tuple.split(',')
            val longitude = parts.getOrNull(0)?.trim()?.toDoubleOrNull()?.takeIf { it.isValidOrdinate(MAX_LONGITUDE) }
            val latitude = parts.getOrNull(1)?.trim()?.toDoubleOrNull()?.takeIf { it.isValidOrdinate(MAX_LATITUDE) }
            if (longitude == null || latitude == null) null else "[$longitude,$latitude]"
        }
    return positions.ifEmpty { null }
}

/** GeoJSON's ordinate bounds, per RFC 7946. */
private const val MAX_LONGITUDE = 180.0
private const val MAX_LATITUDE = 90.0

/**
 * Whether an ordinate is one this converter will write, checked before the number reaches the JSON.
 *
 * `toDoubleOrNull` accepts "NaN" and "Infinity", and these coordinates are interpolated into the output as text — so a
 * single such ordinate emits a bare `NaN` token and makes the *whole* converted file unparseable, taking every other
 * placemark in it down too. That is the same shape of failure as writing `0,498` for a decimal under a comma-decimal
 * locale. Out-of-range values go on the same pass: a coordinate outside RFC 7946's bounds is corrupt input rather than
 * a place on Earth.
 */
private fun Double.isValidOrdinate(limit: Double): Boolean = isFinite() && this in -limit..limit

/** One GeoJSON feature, with the placemark's text and the style's colours as simplestyle properties. */
internal fun KmlGeometry.toFeature(placemark: Placemark, style: KmlStyle?): String {
    val properties = mutableListOf<String>()
    placemark.name?.takeIf { it.isNotBlank() }?.let { properties += "\"title\":${it.jsonString()}" }
    placemark.description?.takeIf { it.isNotBlank() }?.let { properties += "\"description\":${it.jsonString()}" }

    style?.lineColor?.toCssColor()?.let { (hex, opacity) ->
        properties += "\"stroke\":\"$hex\""
        properties += "\"stroke-opacity\":$opacity"
    }
    style?.lineWidth?.let { properties += "\"stroke-width\":$it" }
    if (isPolygonal && style?.filled != false) {
        style?.fillColor?.toCssColor()?.let { (hex, opacity) ->
            properties += "\"fill\":\"$hex\""
            properties += "\"fill-opacity\":$opacity"
        }
    }

    return "{\"type\":\"Feature\",\"properties\":{${properties.joinToString(",")}}," +
        "\"geometry\":{\"type\":\"$type\",\"coordinates\":$coordinates}}"
}

/**
 * A KML colour is `aabbggrr` — alpha first and the channels in the reverse of CSS order.
 *
 * Returns the `#rrggbb` CSS hex plus the alpha as a 0..1 opacity, because simplestyle carries the two separately.
 * Reading these in KML's own byte order is the single easiest thing to get wrong here: it turns red into blue.
 */
internal fun String.toCssColor(): Pair<String, String>? {
    val bytes =
        removePrefix("#").takeIf { it.length == KML_COLOR_LENGTH }?.chunked(BYTE_CHARS)?.map { it.toIntOrNull(HEX) }
    return if (bytes == null || bytes.any { it == null }) {
        null
    } else {
        val opacity = bytes[ALPHA]!!.toDouble() / MAX_CHANNEL
        // Rendered digit by digit rather than through a format string. This is JSON, not display text, and a
        // locale-aware `%f` writes `"fill-opacity":0,498` on a comma-decimal device — invalid JSON, which makes
        // MapLibre reject the whole converted file so every KML import silently draws nothing. The previous
        // implementation pinned Locale.US to avoid that; building the text by hand cannot regress into it, and works
        // the same on every platform.
        "#${bytes[RED]!!.hexByte()}${bytes[GREEN]!!.hexByte()}${bytes[BLUE]!!.hexByte()}" to opacity.toFixed()
    }
}

/** Minimal JSON string escaping — KML descriptions routinely carry quotes, newlines and CDATA-wrapped HTML. */
internal fun String.jsonString(): String {
    val escaped = StringBuilder("\"")
    forEach { character ->
        when {
            character == '"' -> escaped.append("\\\"")
            character == '\\' -> escaped.append("\\\\")
            character == '\n' -> escaped.append("\\n")
            character == '\r' -> escaped.append("\\r")
            character == '\t' -> escaped.append("\\t")
            character < ' ' -> escaped.append("\\u").append(character.code.toString(HEX).padStart(ESCAPE_DIGITS, '0'))
            else -> escaped.append(character)
        }
    }
    return escaped.append('"').toString()
}

private val WHITESPACE = Regex("\\s+")

/** A JSON `\uXXXX` escape is always four hex digits. */
private const val ESCAPE_DIGITS = 4

private const val KML_COLOR_LENGTH = 8
private const val BYTE_CHARS = 2
private const val HEX = 16
private const val MAX_CHANNEL = 255.0

// A KML colour is aabbggrr: alpha first, then the channels in the reverse of CSS order.
private const val ALPHA = 0
private const val BLUE = 1
private const val GREEN = 2
private const val RED = 3

/** A GeoJSON ring needs three distinct positions before it can be closed into a polygon. */
internal const val MIN_RING_POSITIONS = 3
internal const val MIN_LINE_POSITIONS = 2

/** One byte as two lowercase hex digits, the way a CSS colour wants it. */
private fun Int.hexByte(): String = toString(HEX).padStart(BYTE_CHARS, '0')

/** Three decimal places, written out rather than formatted, so no locale can put a comma in the JSON. */
private fun Double.toFixed(): String {
    val scaled = (this * OPACITY_SCALE).roundToLong()
    return "${scaled / OPACITY_SCALE}.${(scaled % OPACITY_SCALE).toString().padStart(OPACITY_DECIMALS, '0')}"
}

private const val OPACITY_SCALE = 1000L
private const val OPACITY_DECIMALS = 3
