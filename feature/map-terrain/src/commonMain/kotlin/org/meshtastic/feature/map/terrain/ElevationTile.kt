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

/**
 * Mapterhorn/Mapzen's Terrarium elevation encoding: `elevation = R×256 + G + B÷256 − 32768`, spec at
 * https://github.com/tilezen/joerd/blob/master/docs/formats.md. Ported from the sibling iOS app's
 * `TerrariumDecoder.swift`, same formula, same 32768m offset (covers the full ±11000m range with margin).
 */
private const val TERRARIUM_OFFSET_METERS = 32768f
private const val TERRARIUM_RED_SCALE = 256f
private const val TERRARIUM_BLUE_SCALE = 256f

fun terrariumElevationMeters(red: Int, green: Int, blue: Int): Float =
    (red * TERRARIUM_RED_SCALE + green + blue / TERRARIUM_BLUE_SCALE) - TERRARIUM_OFFSET_METERS

/**
 * A decoded elevation grid in meters, row-major with (0,0) at the image's top-left — the same orientation as the source
 * tile's own pixels, and as [org.meshtastic.app.map.offline.pmtiles.WebMercatorTileMath]'s tile-local Y (top-to-bottom,
 * matching XYZ tile convention).
 */
class ElevationTile(val width: Int, val height: Int, val elevations: FloatArray) {
    init {
        require(width > 0 && height > 0) { "width ($width) and height ($height) must both be positive" }
        // Long math: width*height can wrap in Int (65536², say) and collide with a tiny elevations.size.
        require(elevations.size.toLong() == width.toLong() * height) {
            "elevations.size (${elevations.size}) must equal width×height ($width×$height)"
        }
    }

    /** Nearest-sample elevation at pixel [x], [y], clamped to the tile's own edge outside its bounds. */
    fun elevationAt(x: Int, y: Int): Float {
        val cx = x.coerceIn(0, width - 1)
        val cy = y.coerceIn(0, height - 1)
        return elevations[cy * width + cx]
    }
}

/**
 * Decodes a Terrarium-encoded WebP tile (Mapterhorn's format) into meters-above-sea-level.
 *
 * Every platform actual must decode with straight, not premultiplied, alpha: elevation lives entirely in the RGB
 * channels, and both Android's [android.graphics.BitmapFactory] and Skia default to alpha-premultiplied output, which
 * would silently scale RGB by alpha and corrupt every partially-transparent pixel's decoded elevation.
 */
expect fun decodeTerrariumTile(webpBytes: ByteArray): ElevationTile
