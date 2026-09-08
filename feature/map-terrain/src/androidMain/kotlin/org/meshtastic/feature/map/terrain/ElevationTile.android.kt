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

import android.graphics.BitmapFactory

actual fun decodeTerrariumTile(webpBytes: ByteArray): ElevationTile {
    // inPremultiplied = false: BitmapFactory premultiplies RGB by alpha by default, which would corrupt the
    // elevation bits encoded in the RGB channels wherever a tile's alpha isn't fully opaque.
    val options = BitmapFactory.Options().apply { inPremultiplied = false }
    val bitmap =
        BitmapFactory.decodeByteArray(webpBytes, 0, webpBytes.size, options)
            ?: error("Could not decode Terrarium WebP tile (${webpBytes.size} bytes)")

    val width = bitmap.width
    val height = bitmap.height
    val pixels = IntArray(width * height)
    bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
    bitmap.recycle()

    val elevations = FloatArray(width * height)
    for (i in pixels.indices) {
        val pixel = pixels[i]
        val red = (pixel ushr RED_SHIFT) and COLOR_MASK
        val green = (pixel ushr GREEN_SHIFT) and COLOR_MASK
        val blue = pixel and COLOR_MASK
        elevations[i] = terrariumElevationMeters(red, green, blue)
    }
    return ElevationTile(width, height, elevations)
}

private const val RED_SHIFT = 16
private const val GREEN_SHIFT = 8
private const val COLOR_MASK = 0xFF
