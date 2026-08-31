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
package org.meshtastic.feature.settings.debugging

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import org.meshtastic.core.repository.MirrorFrame
import org.meshtastic.core.repository.MirrorPalette

// 4x scale for the common 128px-wide OLED; caps the image so the D-pad stays above the fold on desktop.
private val MAX_CANVAS_WIDTH = 512.dp

// MONO_VLSB packs 8 vertically adjacent pixels per byte (one "page" row).
private const val PIXELS_PER_PAGE = 8

/**
 * Renders a MONO_VLSB 1bpp framebuffer once per frame into a 1:1 [ImageBitmap] and scales it up with nearest-neighbor
 * filtering — crisp device pixels, no fractional-scale seams, one pixel walk per frame instead of per recomposition.
 */
@Composable
internal fun MirrorFrameImage(frame: MirrorFrame, palette: MirrorPalette?, modifier: Modifier = Modifier) {
    // A palette only applies when the frame references it; otherwise render monochrome.
    val activePalette = palette?.takeIf { frame.paletteSignature != 0 && it.signature == frame.paletteSignature }
    val bitmap = remember(frame, activePalette) { renderFrame(frame, activePalette) }
    Image(
        bitmap = bitmap,
        contentDescription = "Device screen",
        modifier =
        modifier
            .widthIn(max = MAX_CANVAS_WIDTH)
            .fillMaxWidth()
            .aspectRatio(frame.width.toFloat() / frame.height.toFloat()),
        filterQuality = FilterQuality.None,
    )
}

private class ResolvedRegion(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
    val on: Color,
    val off: Color,
)

private fun rgb565ToColor(v: Int): Color = Color(
    red = ((v ushr 11) and 0x1F) * 255 / 31,
    green = ((v ushr 5) and 0x3F) * 255 / 63,
    blue = (v and 0x1F) * 255 / 31,
)

private fun renderFrame(frame: MirrorFrame, palette: MirrorPalette?): ImageBitmap {
    val defaultOn = palette?.let { rgb565ToColor(it.defaultOnColor) } ?: Color.White
    val defaultOff = palette?.let { rgb565ToColor(it.defaultOffColor) } ?: Color.Black
    val regions =
        palette?.regions.orEmpty().map {
            ResolvedRegion(
                left = it.x,
                top = it.y,
                right = it.x + it.width,
                bottom = it.y + it.height,
                on = rgb565ToColor(it.on_color),
                off = rgb565ToColor(it.off_color),
            )
        }

    val bitmap = ImageBitmap(frame.width, frame.height)
    val size = Size(frame.width.toFloat(), frame.height.toFloat())
    CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(bitmap), size) {
        drawRect(color = defaultOff)
        val pixel = Size(1f, 1f)
        for (y in 0 until frame.height) {
            val page = (y / PIXELS_PER_PAGE) * frame.width
            val bit = 1 shl (y % PIXELS_PER_PAGE)
            // Cull to the regions overlapping this row; the last matching region wins,
            // matching the firmware's precedence.
            val rowRegions = regions.filter { y >= it.top && y < it.bottom }
            for (x in 0 until frame.width) {
                val set = frame.pixels[page + x].toInt() and bit != 0
                val region = rowRegions.lastOrNull { x >= it.left && x < it.right }
                val color =
                    when {
                        region != null -> if (set) region.on else region.off
                        set -> defaultOn
                        else -> defaultOff
                    }
                if (color != defaultOff) {
                    drawRect(color = color, topLeft = Offset(x.toFloat(), y.toFloat()), size = pixel)
                }
            }
        }
    }
    return bitmap
}
