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

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.ByteArrayOutputStream

private const val PNG_QUALITY = 100

internal actual fun decodePngPixels(bytes: ByteArray): RasterPixels? {
    // inPremultiplied = false keeps the colour of a faint pixel intact for the smoothing, which premultiplies itself.
    val options = BitmapFactory.Options().apply { inPremultiplied = false }
    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options) ?: return null
    val pixels = IntArray(bitmap.width * bitmap.height)
    bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
    return RasterPixels(bitmap.width, bitmap.height, pixels).also { bitmap.recycle() }
}

internal actual fun encodePngPixels(pixels: RasterPixels): ByteArray {
    val bitmap = Bitmap.createBitmap(pixels.width, pixels.height, Bitmap.Config.ARGB_8888)
    bitmap.setPixels(pixels.argb, 0, pixels.width, 0, 0, pixels.width, pixels.height)
    val stream = ByteArrayOutputStream()
    bitmap.compress(Bitmap.CompressFormat.PNG, PNG_QUALITY, stream)
    bitmap.recycle()
    return stream.toByteArray()
}
