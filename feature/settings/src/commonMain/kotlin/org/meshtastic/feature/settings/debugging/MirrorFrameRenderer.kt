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
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.repository.MirrorFormat
import org.meshtastic.core.repository.MirrorFrame
import org.meshtastic.core.repository.MirrorPalette
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.mirror_device_screen

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
        contentDescription = stringResource(Res.string.mirror_device_screen),
        modifier =
        modifier
            .widthIn(max = MAX_CANVAS_WIDTH)
            .fillMaxWidth()
            .aspectRatio(frame.width.toFloat() / frame.height.toFloat()),
        filterQuality = FilterQuality.None,
    )
}

/** One colorized rectangle with palette colors pre-resolved to Compose colors. */
internal class ResolvedRegion(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
    val on: Color,
    val off: Color,
)

/** Reads one little-endian RGB565 value from a packed pixel row. */
@Suppress("MagicNumber")
private fun leRgb565At(pixels: ByteArray, index: Int): Int =
    (pixels[index].toInt() and 0xFF) or ((pixels[index + 1].toInt() and 0xFF) shl 8)

/** Expands RGB565 to 8-bit channels by bit replication (the canonical expansion; plain scaling floors). */
@Suppress("MagicNumber")
internal fun rgb565ToColor(v: Int): Color {
    val r5 = (v ushr 11) and 0x1F
    val g6 = (v ushr 5) and 0x3F
    val b5 = v and 0x1F
    return Color(red = (r5 shl 3) or (r5 ushr 2), green = (g6 shl 2) or (g6 ushr 4), blue = (b5 shl 3) or (b5 ushr 2))
}

/**
 * Resolves one pixel against the regions overlapping its row, highest table index winning — the firmware's precedence.
 * [rowRegions] must already be culled to the pixel's row.
 */
internal fun resolvePixelColor(
    x: Int,
    set: Boolean,
    rowRegions: List<ResolvedRegion>,
    defaultOn: Color,
    defaultOff: Color,
): Color {
    for (i in rowRegions.indices.reversed()) {
        val region = rowRegions[i]
        if (x >= region.left && x < region.right) return if (set) region.on else region.off
    }
    return if (set) defaultOn else defaultOff
}

internal fun MirrorPalette.resolveRegions(): List<ResolvedRegion> = regions.map {
    ResolvedRegion(
        left = it.x,
        top = it.y,
        right = it.x + it.width,
        bottom = it.y + it.height,
        on = rgb565ToColor(it.on_color),
        off = rgb565ToColor(it.off_color),
    )
}

private fun renderFrame(frame: MirrorFrame, palette: MirrorPalette?): ImageBitmap = when (frame.format) {
    MirrorFormat.RGB565 -> renderRgb565Frame(frame)
    MirrorFormat.MONO_VLSB -> renderMonoFrame(frame, palette)
}

/** True-color frames (LVGL/MUI devices): little-endian RGB565, drawn as horizontal same-color runs. */
private fun renderRgb565Frame(frame: MirrorFrame): ImageBitmap {
    val bitmap = ImageBitmap(frame.width, frame.height)
    val size = Size(frame.width.toFloat(), frame.height.toFloat())
    CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(bitmap), size) {
        drawRect(color = Color.Black)
        for (y in 0 until frame.height) {
            val rowBase = y * frame.width * 2
            var runStart = 0
            var runColor: Color? = null
            fun flush(endExclusive: Int) {
                val color = runColor
                if (color != null && color != Color.Black) {
                    drawRect(
                        color = color,
                        topLeft = Offset(runStart.toFloat(), y.toFloat()),
                        size = Size((endExclusive - runStart).toFloat(), 1f),
                    )
                }
            }
            for (x in 0 until frame.width) {
                val color = rgb565ToColor(leRgb565At(frame.pixels, rowBase + x * 2))
                if (color != runColor) {
                    flush(x)
                    runStart = x
                    runColor = color
                }
            }
            flush(frame.width)
        }
    }
    return bitmap
}

private fun renderMonoFrame(frame: MirrorFrame, palette: MirrorPalette?): ImageBitmap {
    val defaultOn = palette?.let { rgb565ToColor(it.defaultOnColor) } ?: Color.White
    val defaultOff = palette?.let { rgb565ToColor(it.defaultOffColor) } ?: Color.Black
    val regions = palette?.resolveRegions().orEmpty()

    val bitmap = ImageBitmap(frame.width, frame.height)
    val size = Size(frame.width.toFloat(), frame.height.toFloat())
    CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(bitmap), size) {
        drawRect(color = defaultOff)
        val rowRegions = ArrayList<ResolvedRegion>(regions.size)
        for (y in 0 until frame.height) {
            rowRegions.clear()
            for (region in regions) if (y >= region.top && y < region.bottom) rowRegions.add(region)

            val page = (y / PIXELS_PER_PAGE) * frame.width
            val bit = 1 shl (y % PIXELS_PER_PAGE)
            // Emit horizontal runs of identical color; framebuffers are run-heavy,
            // and one draw per run beats one draw per pixel by orders of magnitude.
            var runStart = 0
            var runColor: Color? = null
            fun flush(endExclusive: Int) {
                val color = runColor
                if (color != null && color != defaultOff) {
                    drawRect(
                        color = color,
                        topLeft = Offset(runStart.toFloat(), y.toFloat()),
                        size = Size((endExclusive - runStart).toFloat(), 1f),
                    )
                }
            }
            for (x in 0 until frame.width) {
                val set = frame.pixels[page + x].toInt() and bit != 0
                val color = resolvePixelColor(x, set, rowRegions, defaultOn, defaultOff)
                if (color != runColor) {
                    flush(x)
                    runStart = x
                    runColor = color
                }
            }
            flush(frame.width)
        }
    }
    return bitmap
}
