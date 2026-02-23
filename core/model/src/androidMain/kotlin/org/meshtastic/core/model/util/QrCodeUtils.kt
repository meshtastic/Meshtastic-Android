/*
 * Copyright (c) 2025-2026 Meshtastic LLC
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
@file:Suppress("MagicNumber", "TooGenericExceptionCaught")

package org.meshtastic.core.model.util

import android.graphics.Bitmap
import co.touchlab.kermit.Logger
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import com.google.zxing.common.BitMatrix
import org.meshtastic.proto.ChannelSet

fun ChannelSet.qrCode(shouldAdd: Boolean): Bitmap? = try {
    val multiFormatWriter = MultiFormatWriter()
    val url = getChannelUrl(false, shouldAdd)
    val bitMatrix = multiFormatWriter.encode(url.toString(), BarcodeFormat.QR_CODE, 960, 960)
    bitMatrix.toBitmap()
} catch (ex: Throwable) {
    Logger.e(ex) { "URL was too complex to render as barcode" }
    null
}

private fun BitMatrix.toBitmap(): Bitmap {
    val width = width
    val height = height
    val pixels = IntArray(width * height)
    for (y in 0 until height) {
        val offset = y * width
        for (x in 0 until width) {
            // Black: 0xFF000000, White: 0xFFFFFFFF
            pixels[offset + x] = if (get(x, y)) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
        }
    }
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
    return bitmap
}
