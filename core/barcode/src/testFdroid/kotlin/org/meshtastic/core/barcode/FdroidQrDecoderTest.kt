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
package org.meshtastic.core.barcode

import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeWriter
import org.junit.Assert.assertEquals
import org.junit.Test

class FdroidQrDecoderTest {

    @Test
    fun `decodes Apple-compatible channel URL from padded camera plane`() {
        val channelUrl = "https://meshtastic.org/e/#CgMSAQESBggBQANIAQ"
        val dimension = 177
        val matrix = QRCodeWriter().encode(channelUrl, BarcodeFormat.QR_CODE, dimension, dimension)
        val packed =
            ByteArray(dimension * dimension) { index ->
                if (matrix[index % dimension, index / dimension]) 0 else 0xFF.toByte()
            }
        val rowStride = dimension + 11
        val padded = ByteArray(rowStride * dimension) { 0x7F }
        for (row in 0 until dimension) {
            packed.copyInto(padded, row * rowStride, row * dimension, (row + 1) * dimension)
        }

        val decoded =
            MultiFormatReader()
                .decode(
                    BinaryBitmap(
                        HybridBinarizer(
                            PlanarYUVLuminanceSource(padded, rowStride, dimension, 0, 0, dimension, dimension, false),
                        ),
                    ),
                )

        assertEquals(channelUrl, decoded.text)
    }
}
