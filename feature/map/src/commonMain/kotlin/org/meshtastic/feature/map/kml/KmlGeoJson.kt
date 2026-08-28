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
    // Only a point is drawn as an icon, and only this converter emits the key — simplestyle has no property for an
    // arbitrary image URL, so `icon-url` is ours rather than something a foreign file will carry.
    if (type == "Point") {
        style?.iconHref?.let { properties += "\"$ICON_URL_PROPERTY\":${it.jsonString()}" }
    }
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

/**
 * The property an icon image URL is written to.
 *
 * Deliberately named rather than borrowed: simplestyle-spec 1.1.0 defines `marker-symbol` (a fixed icon vocabulary),
 * `marker-color` and `marker-size`, and nothing for an arbitrary image. This is an extension of ours, so it is declared
 * in one place and documented as such.
 */
internal const val ICON_URL_PROPERTY = "icon-url"

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

/** One byte as two lowercase hex digits, the way a CSS colour wants it. */
private fun Int.hexByte(): String = toString(HEX).padStart(BYTE_CHARS, '0')

/** Three decimal places, written out rather than formatted, so no locale can put a comma in the JSON. */
private fun Double.toFixed(): String {
    val scaled = (this * OPACITY_SCALE).roundToLong()
    return "${scaled / OPACITY_SCALE}.${(scaled % OPACITY_SCALE).toString().padStart(OPACITY_DECIMALS, '0')}"
}

private const val OPACITY_SCALE = 1000L
private const val OPACITY_DECIMALS = 3
