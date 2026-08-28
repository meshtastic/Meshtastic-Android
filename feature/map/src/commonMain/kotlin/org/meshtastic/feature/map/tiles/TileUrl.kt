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
package org.meshtastic.feature.map.tiles

import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.round

/** Half the circumference of the Web Mercator world in metres — the projection's origin shift. */
private const val ORIGIN_SHIFT_METERS = 20_037_508.342789244

/** Millimetre precision is far finer than any tile boundary; it just keeps the bbox free of exponent notation. */
private const val BBOX_DECIMALS = 3
private const val BBOX_SCALE = 1000L

/** Rotated over for `{s}`, the subdomain placeholder Leaflet-style templates use to spread load. */
private val SUBDOMAINS = listOf("a", "b", "c")

/**
 * Resolves the URL of a single tile from a [RasterTileSpec].
 *
 * MapLibre expands these placeholders itself, so this exists for renderers that do not: the Google map's tile provider
 * is asked for one `x`/`y`/`zoom` at a time and has to hand back a finished URL.
 *
 * Returns null when the tile is outside the source's zoom range, or when the template still holds a placeholder we
 * could not fill — a leftover `{apiKey}` would otherwise be sent to the server with its braces intact, which reads in
 * the logs as a server fault rather than a template the user never finished.
 */
fun RasterTileSpec.tileUrl(x: Int, y: Int, zoom: Int): String? {
    if (tiles.isEmpty() || zoom < minZoom || zoom > maxZoom) return null

    val resolved =
        tiles[rotation(x, y, tiles.size)]
            .replace("{s}", SUBDOMAINS[rotation(x, y, SUBDOMAINS.size)], ignoreCase = true)
            .replace("{z}", zoom.toString(), ignoreCase = true)
            .replace("{x}", x.toString(), ignoreCase = true)
            .replace("{y}", y.toString(), ignoreCase = true)
            .replace("{bbox-epsg-3857}", webMercatorTileBbox(x, y, zoom), ignoreCase = true)

    return resolved.takeUnless { '{' in it }
}

/**
 * The tile's bounds in EPSG:3857 metres, as the `minx,miny,maxx,maxy` string a WMS `BBOX` parameter expects.
 *
 * A WMS source has no notion of `z/x/y`; it is asked for an extent. Rendering the numbers by hand rather than through
 * [Double.toString] keeps them out of exponent notation, which servers reject.
 */
internal fun webMercatorTileBbox(x: Int, y: Int, zoom: Int): String {
    val span = 2.0 * ORIGIN_SHIFT_METERS / 2.0.pow(zoom)
    val west = -ORIGIN_SHIFT_METERS + x * span
    val north = ORIGIN_SHIFT_METERS - y * span
    return listOf(west, north - span, west + span, north).joinToString(",") { it.toPlainDecimal() }
}

/** `Int.mod` rather than `%` so a negative coordinate still lands on a valid index instead of throwing. */
private fun rotation(x: Int, y: Int, size: Int): Int = (x.mod(size) + y.mod(size)).mod(size)

private fun Double.toPlainDecimal(): String {
    val scaled = round(abs(this) * BBOX_SCALE).toLong()
    val sign = if (this < 0 && scaled != 0L) "-" else ""
    return "$sign${scaled / BBOX_SCALE}.${(scaled % BBOX_SCALE).toString().padStart(BBOX_DECIMALS, '0')}"
}
