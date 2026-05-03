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
package org.meshtastic.core.ui.util

import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import qrcode.QRCode

/**
 * Generates a QR code painter directly using the Skia/Compose canvas API in pure Kotlin.
 *
 * This implementation avoids any platform-specific bitmap APIs (like Android's [android.graphics.Bitmap] or Java AWT's
 * BufferedImage), making it fully compatible with Android, Desktop, iOS, and Web.
 */
@Suppress("MagicNumber")
@Composable
fun rememberQrCodePainter(text: String, size: Int = 512): Painter {
    val qrCode = androidx.compose.runtime.remember(text) { QRCode.ofSquares().build(text) }
    val rawMatrix = androidx.compose.runtime.remember(qrCode) { qrCode.rawData }
    val matrixSize = androidx.compose.runtime.remember(qrCode) { rawMatrix.size }
    val quietZone = 4 // QR standard quiet zone is 4 modules on all sides
    val totalModules = matrixSize + (quietZone * 2)

    return androidx.compose.runtime.remember(qrCode, size) {
        val bitmap = ImageBitmap(size, size)
        val canvas = androidx.compose.ui.graphics.Canvas(bitmap)
        val drawScope = CanvasDrawScope()

        drawScope.draw(
            density = Density(1f),
            layoutDirection = LayoutDirection.Ltr,
            canvas = canvas,
            size = androidx.compose.ui.geometry.Size(size.toFloat(), size.toFloat()),
        ) {
            val squareSize = size.toFloat() / totalModules

            // Fill background white
            drawRect(
                color = Color.White,
                topLeft = Offset.Zero,
                size = androidx.compose.ui.geometry.Size(size.toFloat(), size.toFloat()),
            )

            // Draw dark squares
            for (row in 0 until matrixSize) {
                for (col in 0 until matrixSize) {
                    if (rawMatrix[row][col].dark) {
                        drawRect(
                            color = Color.Black,
                            topLeft = Offset((col + quietZone) * squareSize, (row + quietZone) * squareSize),
                            size = Size(squareSize, squareSize),
                        )
                    }
                }
            }
        }
        BitmapPainter(bitmap)
    }
}
