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

import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageInfo

private const val BYTES_PER_PIXEL = 4
private const val GREEN_SHIFT = 8
private const val RED_SHIFT = 16
private const val ALPHA_SHIFT = 24
private const val ALPHA_BYTE = 3

// Skia's Image, Bitmap and Data own native memory, so each is closed as soon as its bytes are copied out.
@Suppress("ReturnCount")
internal actual fun decodePngPixels(bytes: ByteArray): RasterPixels? {
    val image = runCatching { Image.makeFromEncoded(bytes) }.getOrNull() ?: return null
    val bitmap = Bitmap()
    try {
        // UNPREMUL: the smoothing premultiplies itself, and a premultiplied read would round faint pixels twice.
        val info = ImageInfo(image.width, image.height, ColorType.N32, ColorAlphaType.UNPREMUL)
        if (!bitmap.allocPixels(info) || !image.readPixels(bitmap)) return null
        val pixels = IntArray(image.width * image.height) { bitmap.getColor(it % image.width, it / image.width) }
        return RasterPixels(image.width, image.height, pixels)
    } finally {
        bitmap.close()
        image.close()
    }
}

internal actual fun encodePngPixels(pixels: RasterPixels): ByteArray {
    val raw = ByteArray(pixels.argb.size * BYTES_PER_PIXEL)
    for (index in pixels.argb.indices) {
        val argb = pixels.argb[index]
        raw[index * BYTES_PER_PIXEL] = argb.toByte()
        raw[index * BYTES_PER_PIXEL + 1] = (argb shr GREEN_SHIFT).toByte()
        raw[index * BYTES_PER_PIXEL + 2] = (argb shr RED_SHIFT).toByte()
        raw[index * BYTES_PER_PIXEL + ALPHA_BYTE] = (argb ushr ALPHA_SHIFT).toByte()
    }
    val bitmap = Bitmap()
    try {
        val info = ImageInfo(pixels.width, pixels.height, ColorType.BGRA_8888, ColorAlphaType.UNPREMUL)
        check(bitmap.installPixels(info, raw, pixels.width * BYTES_PER_PIXEL)) {
            "Could not wrap the radar tile pixels"
        }
        val image = Image.makeFromBitmap(bitmap)
        try {
            val data = checkNotNull(image.encodeToData(EncodedImageFormat.PNG)) { "PNG encode failed" }
            try {
                return data.bytes
            } finally {
                data.close()
            }
        } finally {
            image.close()
        }
    } finally {
        bitmap.close()
    }
}
